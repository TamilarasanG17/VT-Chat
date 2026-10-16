import hmac
import logging

from fastapi import APIRouter, Depends, Header, Request

from app.models.translation_models import TranslateRequest, TranslateResponse
from app.services.translation_service import TranslationError, TranslationService

log = logging.getLogger("translation")
router = APIRouter()


def get_service(request: Request) -> TranslationService:
    return request.app.state.service


def require_api_key(request: Request, x_internal_api_key: str | None = Header(default=None)) -> None:
    expected: str = request.app.state.api_key
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key.encode(), expected.encode()):
        raise TranslationError(401, "UNAUTHORIZED", "Invalid or missing API key.")


@router.get("/health")
def health(service: TranslationService = Depends(get_service)):
    return {"status": "ok" if service.is_ready() else "loading", "modelReady": service.is_ready()}


@router.post("/translate", response_model=TranslateResponse, dependencies=[Depends(require_api_key)])
def translate(body: TranslateRequest, service: TranslationService = Depends(get_service)):
    return service.translate(body.text, body.targetLanguage)
