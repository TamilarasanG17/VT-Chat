import asyncio
import json
import logging
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import StreamingResponse

from app.models.rag_models import ProcessResult, RagChatRequest, RagChatResponse
from app.routes.chat import require_api_key
from app.services.context_service import AIError
from app.services.document_service import SUPPORTED
from app.services.rag_errors import RagError
from app.services.rag_service import RagService

log = logging.getLogger("ai.routes.rag")
router = APIRouter(prefix="/rag", dependencies=[Depends(require_api_key)])


def get_rag(request: Request) -> RagService:
    return request.app.state.rag


def _sse(event: str, data: dict) -> bytes:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n".encode()


async def _read_body(request: Request, limit: int) -> bytes:
    """The raw file bytes, refused as soon as they exceed the limit (never buffers an unbounded upload)."""
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > limit:
        raise RagError(413, "DOCUMENT_TOO_LARGE", "This document is too large to process.")
    chunks, size = [], 0
    async for part in request.stream():
        size += len(part)
        if size > limit:
            raise RagError(413, "DOCUMENT_TOO_LARGE", "This document is too large to process.")
        chunks.append(part)
    return b"".join(chunks)


@router.post("/documents/process", response_model=ProcessResult)
async def process_document(request: Request, rag: RagService = Depends(get_rag),
                           user_id: int = Query(alias="userId", ge=1), document_id: int = Query(alias="documentId", ge=1),
                           version: int = Query(ge=1), file_type: str = Query(alias="fileType", max_length=8),
                           file_name: str = Query(alias="fileName", max_length=255)):
    """Body = the raw file. Extract -> clean -> chunk -> embed -> store in Qdrant. Returns only when ALL vectors are stored."""
    if file_type.lower().lstrip(".") not in SUPPORTED:
        raise RagError(422, "UNSUPPORTED_TYPE", "This file type is not supported.")
    data = await _read_body(request, rag.settings.rag_max_document_bytes)
    if not data:
        raise RagError(422, "EMPTY_DOCUMENT", "The file is empty.")
    return await asyncio.to_thread(rag.ingest, user_id, document_id, version, file_name, file_type, data)


@router.delete("/documents/{document_id}")
async def delete_document(document_id: int, user_id: int = Query(alias="userId", ge=1), rag: RagService = Depends(get_rag)):
    await asyncio.to_thread(rag.delete_document, user_id, document_id)
    return {"deleted": True}


@router.post("/chat", response_model=RagChatResponse)
async def rag_chat(body: RagChatRequest, rag: RagService = Depends(get_rag)):
    return await rag.answer(body)


@router.post("/chat/stream")
async def rag_chat_stream(body: RagChatRequest, rag: RagService = Depends(get_rag)):
    events = await rag.stream(body)

    async def gen() -> AsyncIterator[bytes]:
        try:
            async for event, data in events:
                yield _sse(event, data)
        except AIError as e:
            yield _sse("error", {"code": e.code, "message": e.message})
        except Exception:
            log.exception("RAG stream failed")
            yield _sse("error", {"code": "LLM_ERROR", "message": "The AI could not finish that answer."})
        finally:
            await events.aclose()

    return StreamingResponse(gen(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})
