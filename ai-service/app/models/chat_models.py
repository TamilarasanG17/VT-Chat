from typing import Literal

from pydantic import BaseModel, Field


class HistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=200_000)


class ChatRequest(BaseModel):
    message: str = Field(max_length=200_000)
    conversationId: str | None = Field(default=None, max_length=64)
    history: list[HistoryMessage] = Field(default_factory=list, max_length=500)


class ChatResponse(BaseModel):
    response: str
    model: str
