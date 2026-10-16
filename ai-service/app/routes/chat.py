import hmac
import json
import logging
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, Header, Request
from fastapi.responses import StreamingResponse

from app.models.chat_models import ChatRequest, ChatResponse
from app.services.ai_service import AIService
from app.services.context_service import AIError

log = logging.getLogger("ai.routes")
router = APIRouter()


def get_service(request: Request) -> AIService:
    return request.app.state.service


def require_api_key(request: Request, x_internal_api_key: str | None = Header(default=None)) -> None:
    expected: str = request.app.state.api_key
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key.encode(), expected.encode()):
        raise AIError(401, "UNAUTHORIZED", "Invalid or missing API key.")


def _sse(event: str, data: dict) -> bytes:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n".encode()


@router.get("/health")
def health(request: Request, service: AIService = Depends(get_service)):
    rag = getattr(request.app.state, "rag_runtime", None)
    return {"status": "ok", "provider": service.settings.provider, "model": service.model,
            "rag": rag.status_dict() if rag else {"enabled": False}}


@router.post("/ai/chat", response_model=ChatResponse, dependencies=[Depends(require_api_key)])
async def chat(body: ChatRequest, service: AIService = Depends(get_service)):
    text = await service.chat(body)
    return ChatResponse(response=text, model=service.model)


@router.post("/ai/chat/stream", dependencies=[Depends(require_api_key)])
async def chat_stream(body: ChatRequest, request: Request, service: AIService = Depends(get_service)):
    first, rest = await service.stream(body)

    async def events() -> AsyncIterator[bytes]:
        try:
            yield _sse("delta", {"text": first})
            async for chunk in rest:
                yield _sse("delta", {"text": chunk})
            yield _sse("done", {"model": service.model})
        except AIError as e:
            yield _sse("error", {"code": e.code, "message": e.message})
        except Exception:
            log.exception("AI stream failed")
            yield _sse("error", {"code": "LLM_ERROR", "message": "The AI could not finish that answer."})
        finally:
            await rest.aclose()

    return StreamingResponse(events(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})
