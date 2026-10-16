"""Orchestration: context -> provider, with logging that never contains prompts, answers or keys."""
from __future__ import annotations

import logging
import time
from collections.abc import AsyncIterator

from app.config import Settings
from app.models.chat_models import ChatRequest
from app.services.context_service import AIError, ContextBuilder
from app.services.llm_service import LLMProvider
from app.services.prompt_service import system_prompt

log = logging.getLogger("ai.service")


class AIService:
    def __init__(self, settings: Settings, provider: LLMProvider):
        self.settings = settings
        self.provider = provider
        self.context = ContextBuilder(settings)

    @property
    def model(self) -> str:
        return self.provider.model

    def _prepare(self, req: ChatRequest) -> list[dict[str, str]]:
        messages = self.context.build(req.message, req.history)
        log.info("AI request received provider=%s model=%s messages=%d chars=%d",
                 self.settings.provider, self.model, len(messages), sum(len(m["content"]) for m in messages))
        return messages

    async def chat(self, req: ChatRequest) -> str:
        messages = self._prepare(req)
        started = time.monotonic()
        try:
            text = await self.provider.complete(system_prompt(self.settings), messages)
        except AIError as e:
            log.warning("AI request failed code=%s", e.code)
            raise
        log.info("AI request completed ms=%d chars=%d", (time.monotonic() - started) * 1000, len(text))
        return text

    async def stream(self, req: ChatRequest) -> tuple[str, AsyncIterator[str]]:
        """Returns (first_chunk, rest). The first chunk is awaited HERE so a failure to start (bad key, provider down,
        timeout) is a normal HTTP error for Spring Boot instead of a half-open event stream."""
        messages = self._prepare(req)
        started = time.monotonic()
        it = self.provider.stream(system_prompt(self.settings), messages)
        try:
            first = await anext(it)
        except StopAsyncIteration:
            await it.aclose()
            raise AIError(502, "LLM_ERROR", "The AI returned an empty answer.") from None
        except AIError as e:
            log.warning("AI request failed code=%s", e.code)
            await it.aclose()
            raise
        except BaseException:
            await it.aclose()
            raise

        async def rest() -> AsyncIterator[str]:
            total = len(first)
            try:
                async for chunk in it:
                    total += len(chunk)
                    yield chunk
                log.info("AI request completed ms=%d chars=%d", (time.monotonic() - started) * 1000, total)
            finally:
                await it.aclose()

        return first, rest()
