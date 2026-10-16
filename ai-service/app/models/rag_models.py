from pydantic import BaseModel, Field

from app.models.chat_models import HistoryMessage


class RagChatRequest(BaseModel):
    message: str = Field(max_length=200_000)
    conversationId: str | None = Field(default=None, max_length=64)
    history: list[HistoryMessage] = Field(default_factory=list, max_length=500)
    userId: int = Field(ge=1)
    documentIds: list[int] = Field(min_length=1, max_length=500)


class SourceModel(BaseModel):
    documentId: int
    fileName: str
    pageNumber: int | None = None
    chunkIndex: int = 0


class RagChatResponse(BaseModel):
    response: str
    model: str
    grounded: bool
    sources: list[SourceModel]


class ProcessResult(BaseModel):
    chunkCount: int
    pageCount: int
    processingVersion: int
