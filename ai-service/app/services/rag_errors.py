from app.services.context_service import AIError


class RagError(AIError):
    """A RAG/ingestion failure with a stable code. `message` is safe to show; details only go to the log."""


def unavailable(reason_code: str = "RAG_UNAVAILABLE", message: str = "Knowledge search is temporarily unavailable.") -> RagError:
    return RagError(503, reason_code, message)
