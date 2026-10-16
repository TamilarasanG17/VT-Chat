"""Turns (history, new message) into the message list sent to the model, with hard size limits."""
from __future__ import annotations

from typing import TYPE_CHECKING

from app.config import Settings

if TYPE_CHECKING:
    from app.models.chat_models import HistoryMessage


class AIError(Exception):
    """An error with a stable code, an HTTP status for Spring Boot and a safe, user-facing message."""

    def __init__(self, status: int, code: str, message: str, retry_after: int | None = None):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.retry_after = retry_after


class ContextBuilder:
    def __init__(self, settings: Settings):
        self.max_messages = settings.max_context_messages
        self.max_message_length = settings.max_message_length
        self.max_chars = settings.max_context_chars

    def build(self, message: str, history: list[HistoryMessage]) -> list[dict[str, str]]:
        text = (message or "").strip()
        if not text:
            raise AIError(400, "INVALID_REQUEST", "Message must not be empty.")
        if len(text) > self.max_message_length:
            raise AIError(400, "MESSAGE_TOO_LONG", f"Message is too long (max {self.max_message_length} characters).")
        if len(text) > self.max_chars:
            raise AIError(413, "CONTEXT_TOO_LARGE", "This message is too large for the AI.")

        budget = self.max_chars - len(text)
        kept: list[dict[str, str]] = []
        for item in reversed(history):
            content = (item.content or "").strip()
            if not content:
                continue
            if len(kept) >= self.max_messages or len(content) > budget:
                break
            kept.append({"role": item.role, "content": content})
            budget -= len(content)
        kept.reverse()

        while kept and kept[0]["role"] != "user":
            kept.pop(0)

        kept.append({"role": "user", "content": text})
        return self._merge_same_role(kept)

    @staticmethod
    def _merge_same_role(messages: list[dict[str, str]]) -> list[dict[str, str]]:
        """After a failed generation the history can hold two user messages in a row; some providers reject that."""
        merged: list[dict[str, str]] = []
        for m in messages:
            if merged and merged[-1]["role"] == m["role"]:
                merged[-1] = {"role": m["role"], "content": merged[-1]["content"] + "\n\n" + m["content"]}
            else:
                merged.append(dict(m))
        return merged
