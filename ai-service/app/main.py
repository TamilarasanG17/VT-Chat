"""VTChat AI service. LLM chat ONLY: no users, no auth system, no database, no translation. Spring Boot is its only client."""
import contextvars
import logging
import re
import threading
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings, load_settings
from app.routes import chat as chat_routes
from app.routes import knowledge as knowledge_routes
from app.services.ai_service import AIService
from app.services.rag_service import RagRuntime, RagService
from app.services.context_service import AIError
from app.services.llm_service import LLMProvider, create_provider

request_id_var: contextvars.ContextVar[str] = contextvars.ContextVar("request_id", default="-")
_REQUEST_ID = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


class _RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_var.get()
        return True


def _configure_logging() -> None:
    handler = logging.StreamHandler()
    handler.addFilter(_RequestIdFilter())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [requestId=%(request_id)s] %(name)s: %(message)s"))
    root = logging.getLogger()
    if not any(isinstance(f, _RequestIdFilter) for h in root.handlers for f in h.filters):
        root.handlers = [handler]
    root.setLevel(logging.INFO)
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)


class RequestIdMiddleware:
    """Pure ASGI (not BaseHTTPMiddleware) so streaming and client-disconnect cancellation keep working.
    Uses the caller's X-Request-Id when it is well-formed, otherwise makes one, and echoes it in the response."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        rid = None
        for k, v in scope.get("headers", []):
            if k == b"x-request-id":
                candidate = v.decode("latin-1")
                if _REQUEST_ID.match(candidate):
                    rid = candidate
                break
        rid = rid or uuid.uuid4().hex
        token = request_id_var.set(rid)

        async def send_with_id(message):
            if message["type"] == "http.response.start":
                message.setdefault("headers", []).append((b"x-request-id", rid.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_id)
        finally:
            request_id_var.reset(token)


def create_app(settings: Settings | None = None, provider: LLMProvider | None = None, rag_runtime: RagRuntime | None = None) -> FastAPI:
    _configure_logging()
    log = logging.getLogger("ai")
    settings = settings or load_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        settings.validate()
        prov = provider or create_provider(settings)
        app.state.api_key = settings.api_key
        app.state.service = AIService(settings, prov)
        runtime = rag_runtime or RagRuntime(settings)
        app.state.rag_runtime = runtime
        app.state.rag = RagService(settings, runtime, prov)
        if settings.rag_enabled and rag_runtime is None:
            threading.Thread(target=runtime.ensure_ready, name="rag-warmup", daemon=True).start()
        log.info("AI service ready provider=%s model=%s", settings.provider, prov.model)
        yield
        await prov.aclose()

    app = FastAPI(title="VTChat AI Service", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.include_router(chat_routes.router)
    app.include_router(knowledge_routes.router)
    app.add_middleware(RequestIdMiddleware)

    @app.exception_handler(AIError)
    async def _ai_error(_: Request, exc: AIError):
        headers = {"Retry-After": str(exc.retry_after)} if exc.retry_after else None
        body = {"success": False, "code": exc.code, "message": exc.message}
        if exc.retry_after:
            body["retryAfterSeconds"] = exc.retry_after
        return JSONResponse(status_code=exc.status, content=body, headers=headers)

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError):
        return JSONResponse(status_code=422, content={"success": False, "code": "INVALID_REQUEST", "message": "Invalid AI request."})

    @app.exception_handler(Exception)
    async def _unexpected(_: Request, exc: Exception):
        log.exception("Unexpected AI service failure")
        return JSONResponse(status_code=500, content={"success": False, "code": "AI_ERROR", "message": "AI service error."})

    return app


app = create_app()
