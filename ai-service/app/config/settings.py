"""Settings from environment variables (a local .env file is loaded when python-dotenv is installed)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

PROVIDERS: dict[str, tuple[str, str, bool]] = {
    "openai": ("openai", "https://api.openai.com/v1", True),
    "groq": ("openai", "https://api.groq.com/openai/v1", True),
    "openrouter": ("openai", "https://openrouter.ai/api/v1", True),
    "gemini": ("openai", "https://generativelanguage.googleapis.com/v1beta/openai", True),
    "ollama": ("openai", "http://localhost:11434/v1", False),
    "openai-compatible": ("openai", "", False),
    "anthropic": ("anthropic", "https://api.anthropic.com", True),
    "mock": ("mock", "", False),
}

DEFAULT_SYSTEM_PROMPT = (
    "You are VTChat AI, a helpful and concise assistant.\n"
    "Answer clearly and accurately.\n"
    "If the user asks for code, provide practical examples.\n"
    "Do not claim to have access to private VTChat data unless it has explicitly been provided."
)


class ConfigError(RuntimeError):
    """Raised at startup for a missing/invalid setting. The message never contains a secret value."""


@dataclass(frozen=True)
class Settings:
    host: str = "0.0.0.0"
    port: int = 8002
    api_key: str = ""
    provider: str = "mock"
    model: str = ""
    llm_api_key: str = field(default="", repr=False)
    base_url: str = ""
    llm_timeout_ms: int = 30000
    stream_max_ms: int = 120000
    max_output_tokens: int = 1024
    temperature: float = 0.3
    max_context_messages: int = 20
    max_message_length: int = 4000
    max_context_chars: int = 24000
    system_prompt: str = DEFAULT_SYSTEM_PROMPT

    rag_enabled: bool = True
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: str = field(default="", repr=False)
    qdrant_collection: str = "vtchat_documents"
    qdrant_timeout_ms: int = 10000
    embedding_provider: str = "mock"
    embedding_model: str = "intfloat/multilingual-e5-small"
    embedding_dimension: int = 384
    embedding_base_url: str = ""
    embedding_api_key: str = field(default="", repr=False)
    embedding_query_prefix: str | None = None
    embedding_passage_prefix: str | None = None
    embedding_batch_size: int = 32
    embedding_timeout_ms: int = 30000
    rag_chunk_size: int = 800
    rag_chunk_overlap: int = 120
    rag_min_chunk_chars: int = 40
    rag_top_k: int = 5
    rag_max_context_chunks: int = 5
    rag_max_context_chars: int = 8000
    rag_similarity_threshold: float | None = None
    rag_max_query_length: int = 2000
    rag_max_document_bytes: int = 50 * 1024 * 1024
    rag_max_pages: int = 1000
    rag_max_document_chars: int = 3_000_000
    rag_max_concurrent_ingest: int = 2
    rag_system_prompt: str = ""

    @property
    def protocol(self) -> str:
        return PROVIDERS[self.provider][0]

    @property
    def effective_base_url(self) -> str:
        return (self.base_url or PROVIDERS[self.provider][1]).rstrip("/")

    @property
    def similarity_threshold(self) -> float:
        if self.rag_similarity_threshold is not None:
            return self.rag_similarity_threshold
        return 0.15 if self.embedding_provider == "mock" else 0.0

    def validate(self) -> None:
        if not self.api_key:
            raise ConfigError("AI_SERVICE_API_KEY must be set; refusing to start without it.")
        if self.provider not in PROVIDERS:
            raise ConfigError(f"LLM_PROVIDER must be one of: {', '.join(sorted(PROVIDERS))}.")
        if self.provider != "mock":
            if not self.model:
                raise ConfigError("LLM_MODEL must be set for this LLM_PROVIDER.")
            if PROVIDERS[self.provider][2] and not self.llm_api_key:
                raise ConfigError(f"LLM_API_KEY must be set for LLM_PROVIDER={self.provider}.")
            if not self.effective_base_url:
                raise ConfigError("LLM_BASE_URL must be set for LLM_PROVIDER=openai-compatible.")
            if self.protocol == "anthropic" and self.max_output_tokens <= 0:
                raise ConfigError("LLM_MAX_OUTPUT_TOKENS must be > 0 for anthropic.")
        if self.rag_enabled:
            if self.embedding_provider not in ("mock", "local", "openai-compatible"):
                raise ConfigError("EMBEDDING_PROVIDER must be mock, local or openai-compatible.")
            if self.embedding_dimension < 1:
                raise ConfigError("EMBEDDING_DIMENSION must be set to the embedding model's output size.")
            if self.embedding_provider != "mock" and not self.embedding_model:
                raise ConfigError("EMBEDDING_MODEL must be set.")
            if self.embedding_provider == "openai-compatible" and not self.embedding_base_url:
                raise ConfigError("EMBEDDING_BASE_URL must be set for EMBEDDING_PROVIDER=openai-compatible.")
            if self.rag_chunk_size < 100 or not (0 <= self.rag_chunk_overlap < self.rag_chunk_size):
                raise ConfigError("RAG_CHUNK_SIZE must be >= 100 and RAG_CHUNK_OVERLAP must be >= 0 and < RAG_CHUNK_SIZE.")
            if self.rag_top_k < 1 or self.rag_max_context_chunks < 1 or self.rag_max_context_chars < 200:
                raise ConfigError("RAG_TOP_K / RAG_MAX_CONTEXT_CHUNKS / RAG_MAX_CONTEXT_CHARS are too small.")
            if not self.qdrant_url or not self.qdrant_collection:
                raise ConfigError("QDRANT_URL and QDRANT_COLLECTION must be set.")
        if self.max_context_messages < 0 or self.max_message_length < 1 or self.max_context_chars < self.max_message_length:
            raise ConfigError("AI_MAX_CONTEXT_MESSAGES / AI_MAX_MESSAGE_LENGTH / AI_MAX_CONTEXT_CHARS are inconsistent.")


def _int(name: str, default: int) -> int:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError as e:
        raise ConfigError(f"{name} must be an integer.") from e


def _bool(name: str, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    return default if not raw else raw not in ("false", "0", "no", "off")


def _opt_str(name: str) -> str | None:
    """Unset -> None (automatic); set to an empty string is not distinguishable in .env files, so use 'none'."""
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return None
    return "" if raw.strip().lower() == "none" else raw


def _opt_float(name: str) -> float | None:
    raw = os.getenv(name, "").strip()
    if not raw:
        return None
    try:
        return float(raw)
    except ValueError as e:
        raise ConfigError(f"{name} must be a number.") from e


def _load_prompt() -> str:
    path = os.getenv("AI_SYSTEM_PROMPT_FILE", "").strip()
    if path:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read().strip()
        except OSError as e:
            raise ConfigError("AI_SYSTEM_PROMPT_FILE cannot be read.") from e
        if text:
            return text
    inline = os.getenv("AI_SYSTEM_PROMPT", "").strip()
    return inline or DEFAULT_SYSTEM_PROMPT


def load_settings() -> Settings:
    try:
        temperature = float(os.getenv("LLM_TEMPERATURE", "0.3") or "0.3")
    except ValueError as e:
        raise ConfigError("LLM_TEMPERATURE must be a number.") from e
    return Settings(
        host=os.getenv("AI_SERVICE_HOST", "0.0.0.0"),
        port=_int("AI_SERVICE_PORT", 8002),
        api_key=os.getenv("AI_SERVICE_API_KEY", "").strip(),
        provider=os.getenv("LLM_PROVIDER", "mock").strip().lower() or "mock",
        model=os.getenv("LLM_MODEL", "").strip(),
        llm_api_key=os.getenv("LLM_API_KEY", "").strip(),
        base_url=os.getenv("LLM_BASE_URL", "").strip(),
        llm_timeout_ms=_int("LLM_TIMEOUT_MS", 30000),
        stream_max_ms=_int("AI_STREAM_MAX_MS", 120000),
        max_output_tokens=_int("LLM_MAX_OUTPUT_TOKENS", 1024),
        temperature=temperature,
        max_context_messages=_int("AI_MAX_CONTEXT_MESSAGES", 20),
        max_message_length=_int("AI_MAX_MESSAGE_LENGTH", 4000),
        max_context_chars=_int("AI_MAX_CONTEXT_CHARS", 24000),
        system_prompt=_load_prompt(),
        rag_enabled=_bool("RAG_ENABLED", True),
        qdrant_url=os.getenv("QDRANT_URL", "http://localhost:6333").strip().rstrip("/"),
        qdrant_api_key=os.getenv("QDRANT_API_KEY", "").strip(),
        qdrant_collection=os.getenv("QDRANT_COLLECTION", "vtchat_documents").strip(),
        qdrant_timeout_ms=_int("QDRANT_TIMEOUT_MS", 10000),
        embedding_provider=os.getenv("EMBEDDING_PROVIDER", "mock").strip().lower() or "mock",
        embedding_model=os.getenv("EMBEDDING_MODEL", "intfloat/multilingual-e5-small").strip(),
        embedding_dimension=_int("EMBEDDING_DIMENSION", 384),
        embedding_base_url=os.getenv("EMBEDDING_BASE_URL", "").strip().rstrip("/"),
        embedding_api_key=os.getenv("EMBEDDING_API_KEY", "").strip(),
        embedding_query_prefix=_opt_str("EMBEDDING_QUERY_PREFIX"),
        embedding_passage_prefix=_opt_str("EMBEDDING_PASSAGE_PREFIX"),
        embedding_batch_size=_int("EMBEDDING_BATCH_SIZE", 32),
        embedding_timeout_ms=_int("EMBEDDING_TIMEOUT_MS", 30000),
        rag_chunk_size=_int("RAG_CHUNK_SIZE", 800),
        rag_chunk_overlap=_int("RAG_CHUNK_OVERLAP", 120),
        rag_min_chunk_chars=_int("RAG_MIN_CHUNK_CHARS", 40),
        rag_top_k=_int("RAG_TOP_K", 5),
        rag_max_context_chunks=_int("RAG_MAX_CONTEXT_CHUNKS", 5),
        rag_max_context_chars=_int("RAG_MAX_CONTEXT_CHARS", 8000),
        rag_similarity_threshold=_opt_float("RAG_SIMILARITY_THRESHOLD"),
        rag_max_query_length=_int("RAG_MAX_QUERY_LENGTH", 2000),
        rag_max_document_bytes=_int("RAG_MAX_DOCUMENT_MB", 50) * 1024 * 1024,
        rag_max_pages=_int("RAG_MAX_PAGES", 1000),
        rag_max_document_chars=_int("RAG_MAX_DOCUMENT_CHARS", 3_000_000),
        rag_max_concurrent_ingest=_int("RAG_MAX_CONCURRENT_INGEST", 2),
        rag_system_prompt=os.getenv("RAG_SYSTEM_PROMPT", "").strip(),
    )
