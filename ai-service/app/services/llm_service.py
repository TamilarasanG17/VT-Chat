"""LLM providers behind one small interface. Swapping the model/provider is a configuration change only."""
from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator

import httpx

from app.config import Settings
from app.services.context_service import AIError

log = logging.getLogger("ai.llm")

_UNAVAILABLE = "AI service is temporarily unavailable. Please try again later."


def unavailable() -> AIError:
    return AIError(503, "LLM_UNAVAILABLE", _UNAVAILABLE)


def timeout_error() -> AIError:
    return AIError(504, "LLM_TIMEOUT", "The AI took too long to respond. Please try again.")


def _context_hint(body: str) -> bool:
    b = body.lower()
    return any(k in b for k in ("context_length", "context length", "maximum context", "too many tokens", "prompt is too long", "too long"))


def error_from_status(status: int, body: str, retry_after: str | None) -> AIError:
    """Maps a provider HTTP error to our codes. Only the status is logged by callers; the body is never returned."""
    if status in (401, 403):
        return AIError(502, "LLM_AUTH", _UNAVAILABLE)
    if status == 429:
        try:
            seconds = max(1, min(int(float(retry_after)), 3600)) if retry_after else 30
        except ValueError:
            seconds = 30
        return AIError(429, "LLM_RATE_LIMITED", "The AI is busy right now. Please try again shortly.", seconds)
    if status in (400, 413, 422) and _context_hint(body):
        return AIError(413, "CONTEXT_TOO_LARGE", "This conversation is too long for the AI. Please start a new chat.")
    if status >= 500:
        return unavailable()
    return AIError(502, "LLM_ERROR", "The AI could not answer that request.")


class LLMProvider:
    name = "base"

    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self.settings = settings
        self.model = settings.model
        t = settings.llm_timeout_ms / 1000
        self._client = httpx.AsyncClient(
            base_url=settings.effective_base_url or "http://unused.invalid",
            timeout=httpx.Timeout(connect=min(t, 10), read=t, write=10, pool=5),
            transport=transport,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def complete(self, system: str, messages: list[dict[str, str]]) -> str:
        raise NotImplementedError

    def stream(self, system: str, messages: list[dict[str, str]]) -> AsyncIterator[str]:
        raise NotImplementedError

    async def _post_json(self, path: str, headers: dict, payload: dict) -> dict:
        try:
            async with asyncio.timeout(self.settings.llm_timeout_ms / 1000):
                resp = await self._client.post(path, headers=headers, json=payload)
        except (TimeoutError, httpx.TimeoutException):
            log.warning("LLM timeout provider=%s", self.settings.provider)
            raise timeout_error() from None
        except httpx.TransportError:
            log.warning("LLM transport failure provider=%s", self.settings.provider)
            raise unavailable() from None
        if resp.status_code >= 400:
            log.warning("LLM provider error provider=%s status=%s", self.settings.provider, resp.status_code)
            raise error_from_status(resp.status_code, resp.text[:2000], resp.headers.get("retry-after"))
        try:
            return resp.json()
        except ValueError:
            raise AIError(502, "LLM_ERROR", "The AI returned an unreadable answer.") from None

    async def _stream_lines(self, path: str, headers: dict, payload: dict) -> AsyncIterator[str]:
        deadline = time.monotonic() + self.settings.stream_max_ms / 1000
        try:
            async with self._client.stream("POST", path, headers=headers, json=payload) as resp:
                if resp.status_code >= 400:
                    body = (await resp.aread()).decode("utf-8", "replace")[:2000]
                    log.warning("LLM provider error provider=%s status=%s", self.settings.provider, resp.status_code)
                    raise error_from_status(resp.status_code, body, resp.headers.get("retry-after"))
                async for line in resp.aiter_lines():
                    if time.monotonic() > deadline:
                        log.warning("LLM stream exceeded AI_STREAM_MAX_MS provider=%s", self.settings.provider)
                        raise timeout_error()
                    yield line
        except httpx.TimeoutException:
            log.warning("LLM timeout provider=%s", self.settings.provider)
            raise timeout_error() from None
        except httpx.TransportError:
            log.warning("LLM transport failure provider=%s", self.settings.provider)
            raise unavailable() from None


class OpenAICompatibleProvider(LLMProvider):
    """OpenAI, Groq, OpenRouter, Gemini (OpenAI endpoint), Ollama, vLLM, LM Studio ... : POST {base}/chat/completions."""
    name = "openai"

    def _request(self, system: str, messages: list[dict[str, str]], stream: bool) -> tuple[dict, dict]:
        headers = {"Content-Type": "application/json"}
        if self.settings.llm_api_key:
            headers["Authorization"] = f"Bearer {self.settings.llm_api_key}"
        payload: dict = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}, *messages],
            "temperature": self.settings.temperature,
            "stream": stream,
        }
        if self.settings.max_output_tokens > 0:
            key = "max_completion_tokens" if self.settings.provider == "openai" else "max_tokens"
            payload[key] = self.settings.max_output_tokens
        return headers, payload

    async def complete(self, system: str, messages: list[dict[str, str]]) -> str:
        headers, payload = self._request(system, messages, False)
        data = await self._post_json("/chat/completions", headers, payload)
        try:
            text = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise AIError(502, "LLM_ERROR", "The AI returned an unreadable answer.") from None
        if not isinstance(text, str) or not text.strip():
            raise AIError(502, "LLM_ERROR", "The AI returned an empty answer.")
        return text

    async def stream(self, system: str, messages: list[dict[str, str]]) -> AsyncIterator[str]:
        headers, payload = self._request(system, messages, True)
        lines = self._stream_lines("/chat/completions", headers, payload)
        try:
            async for line in lines:
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    return
                try:
                    obj = json.loads(data)
                except ValueError:
                    continue
                if isinstance(obj, dict) and obj.get("error"):
                    log.warning("LLM stream error event provider=%s", self.settings.provider)
                    raise AIError(502, "LLM_ERROR", "The AI could not finish that answer.")
                try:
                    delta = obj["choices"][0]["delta"].get("content")
                except (KeyError, IndexError, TypeError, AttributeError):
                    continue
                if delta:
                    yield delta
        finally:
            await lines.aclose()


class AnthropicProvider(LLMProvider):
    name = "anthropic"

    def _request(self, system: str, messages: list[dict[str, str]], stream: bool) -> tuple[dict, dict]:
        headers = {
            "Content-Type": "application/json",
            "x-api-key": self.settings.llm_api_key,
            "anthropic-version": "2023-06-01",
        }
        payload = {
            "model": self.model,
            "system": system,
            "messages": messages,
            "max_tokens": self.settings.max_output_tokens,
            "temperature": self.settings.temperature,
            "stream": stream,
        }
        return headers, payload

    async def complete(self, system: str, messages: list[dict[str, str]]) -> str:
        headers, payload = self._request(system, messages, False)
        data = await self._post_json("/v1/messages", headers, payload)
        try:
            text = "".join(b.get("text", "") for b in data["content"] if b.get("type") == "text")
        except (KeyError, TypeError, AttributeError):
            raise AIError(502, "LLM_ERROR", "The AI returned an unreadable answer.") from None
        if not text.strip():
            raise AIError(502, "LLM_ERROR", "The AI returned an empty answer.")
        return text

    async def stream(self, system: str, messages: list[dict[str, str]]) -> AsyncIterator[str]:
        headers, payload = self._request(system, messages, True)
        lines = self._stream_lines("/v1/messages", headers, payload)
        try:
            async for line in lines:
                if not line.startswith("data:"):
                    continue
                try:
                    obj = json.loads(line[5:].strip())
                except ValueError:
                    continue
                kind = obj.get("type") if isinstance(obj, dict) else None
                if kind == "content_block_delta":
                    delta = obj.get("delta") or {}
                    if delta.get("type") == "text_delta" and delta.get("text"):
                        yield delta["text"]
                elif kind == "message_stop":
                    return
                elif kind == "error":
                    etype = (obj.get("error") or {}).get("type", "")
                    log.warning("LLM stream error event provider=anthropic type=%s", etype)
                    if etype == "overloaded_error":
                        raise unavailable()
                    if etype == "rate_limit_error":
                        raise AIError(429, "LLM_RATE_LIMITED", "The AI is busy right now. Please try again shortly.", 30)
                    raise AIError(502, "LLM_ERROR", "The AI could not finish that answer.")
        finally:
            await lines.aclose()


class MockProvider(LLMProvider):
    """No network, no key. Lets you run and test the whole application before choosing a real provider.
    Start a message with [[fail:timeout]], [[fail:unavailable]], [[fail:auth]], [[fail:busy]] or [[fail:context]]
    to simulate a provider failure."""
    name = "mock"

    def __init__(self, settings: Settings, transport=None):
        self.settings = settings
        self.model = settings.model or "mock-model"

    async def aclose(self) -> None:
        return None

    @staticmethod
    def _maybe_fail(messages: list[dict[str, str]]) -> None:
        last = messages[-1]["content"].strip()
        if last.startswith("[[fail:"):
            kind = last[7:].split("]]", 1)[0]
            raise {
                "timeout": timeout_error(),
                "unavailable": unavailable(),
                "auth": AIError(502, "LLM_AUTH", _UNAVAILABLE),
                "busy": AIError(429, "LLM_RATE_LIMITED", "The AI is busy right now. Please try again shortly.", 20),
                "context": AIError(413, "CONTEXT_TOO_LARGE", "This conversation is too long for the AI. Please start a new chat."),
            }.get(kind, AIError(502, "LLM_ERROR", "The AI could not answer that request."))

    @staticmethod
    def _answer(messages: list[dict[str, str]]) -> str:
        last = messages[-1]["content"].strip()
        earlier = sum(1 for m in messages[:-1] if m["role"] == "user")
        return (f"**Mock AI** (no real model is configured).\n\nYou said: {last[:300]}\n\n"
                f"I can see {earlier} earlier question(s) in this conversation.\n\n"
                "```python\nprint('set LLM_PROVIDER to use a real model')\n```")

    async def complete(self, system: str, messages: list[dict[str, str]]) -> str:
        self._maybe_fail(messages)
        return self._answer(messages)

    async def stream(self, system: str, messages: list[dict[str, str]]) -> AsyncIterator[str]:
        self._maybe_fail(messages)
        for word in self._answer(messages).split(" "):
            await asyncio.sleep(0.02)
            yield word + " "


def create_provider(settings: Settings, transport: httpx.AsyncBaseTransport | None = None) -> LLMProvider:
    return {"openai": OpenAICompatibleProvider, "anthropic": AnthropicProvider, "mock": MockProvider}[settings.protocol](settings, transport)
