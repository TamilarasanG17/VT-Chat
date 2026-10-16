"""VTChat translation microservice. Translation ONLY: no auth/users/messages/DB; Spring Boot is its only client."""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import load_settings
from app.routes import translation as translation_routes
from app.services.translation_service import IndicTrans2Engine, TranslationError, TranslationService

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("translation")


def create_app(service: TranslationService | None = None, api_key: str | None = None) -> FastAPI:
    settings = load_settings()
    key = settings.api_key if api_key is None else api_key

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if not key:
            raise RuntimeError("TRANSLATION_SERVICE_API_KEY must be set; refusing to start without it.")
        app.state.api_key = key
        if service is not None:
            app.state.service = service
        else:
            engine = IndicTrans2Engine(settings)
            app.state.service = TranslationService(engine)
            if settings.preload:
                engine.load()
        yield

    app = FastAPI(title="VTChat Translation Service", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.include_router(translation_routes.router)

    @app.exception_handler(TranslationError)
    async def _translation_error(_: Request, exc: TranslationError):
        return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message})

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError):
        return JSONResponse(status_code=422, content={"code": "INVALID_REQUEST", "message": "Invalid translation request."})

    @app.exception_handler(Exception)
    async def _unexpected(_: Request, exc: Exception):
        log.exception("Unexpected translation failure")
        return JSONResponse(status_code=500, content={"code": "TRANSLATION_FAILED", "message": "Unable to translate this message right now."})

    return app


app = create_app()
