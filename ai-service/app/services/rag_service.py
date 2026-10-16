"""RAG orchestration inside the existing AI service: ingestion, deletion, retrieval and the grounded answer.
It reuses the Phase 11 LLM provider and context builder (there is no second LLM service)."""
from __future__ import annotations

import asyncio
import logging
import threading
import time
from collections.abc import AsyncIterator

from app.config import Settings
from app.models.rag_models import ProcessResult, RagChatRequest, RagChatResponse, SourceModel
from app.services.chunking_service import chunk_pages
from app.services.context_service import AIError, ContextBuilder
from app.services.document_service import extract_pages
from app.services.embedding_service import EmbeddingProvider, create_embedding_provider
from app.services.llm_service import LLMProvider
from app.services.rag_errors import RagError, unavailable
from app.services.retrieval_service import (DEFAULT_RAG_SYSTEM_PROMPT, NOT_FOUND_TEXT, StreamGate, build_sources,
                                            format_context, is_no_answer, select_context, wrap_question)
from app.vector.qdrant_service import QdrantStore, build_filter, build_points

log = logging.getLogger("ai.rag")
_RETRY_AFTER_FAILURE_S = 30


class RagRuntime:
    """The embedding model and the Qdrant collection, prepared lazily and shared. A failure here (Qdrant down, model
    missing, dimension mismatch) only disables RAG: normal AI chat is not affected, and it retries on demand."""

    def __init__(self, settings: Settings, embedder: EmbeddingProvider | None = None, store: QdrantStore | None = None):
        self.settings = settings
        self._embedder = embedder
        self._store = store
        self._lock = threading.Lock()
        self._failed_at = 0.0
        self.status = "disabled" if not settings.rag_enabled else "loading"
        self.error = ""

    @property
    def embedder(self) -> EmbeddingProvider:
        return self._embedder

    @property
    def store(self) -> QdrantStore:
        return self._store

    def ensure_ready(self) -> bool:
        if not self.settings.rag_enabled:
            return False
        with self._lock:
            if self.status == "ready":
                return True
            if self.status == "unavailable" and time.monotonic() - self._failed_at < _RETRY_AFTER_FAILURE_S:
                return False
            try:
                if self._embedder is None:
                    self._embedder = create_embedding_provider(self.settings)
                if self._store is None:
                    self._store = QdrantStore(self.settings)
                self._store.ensure_collection()
                self.status, self.error = "ready", ""
                log.info("RAG ready embedding=%s model=%s dimension=%d collection=%s", self._embedder.name,
                         self._embedder.model, self._embedder.dimension, self.settings.qdrant_collection)
                return True
            except AIError as e:
                self.status, self.error, self._failed_at = "unavailable", e.code, time.monotonic()
                log.error("RAG unavailable code=%s detail=%s", e.code, e.message)
            except Exception:
                self.status, self.error, self._failed_at = "unavailable", "RAG_INIT_FAILED", time.monotonic()
                log.exception("RAG initialisation failed")
            return False

    def require(self) -> None:
        if not self.settings.rag_enabled:
            raise RagError(503, "RAG_DISABLED", "Knowledge search is disabled on this server.")
        if not self.ensure_ready():
            raise unavailable()

    def status_dict(self) -> dict:
        d = {"enabled": self.settings.rag_enabled, "status": self.status, "embeddingProvider": self.settings.embedding_provider,
             "embeddingDimension": self.settings.embedding_dimension}
        if self.error:
            d["error"] = self.error
        return d


class RagService:
    def __init__(self, settings: Settings, runtime: RagRuntime, provider: LLMProvider):
        self.settings = settings
        self.runtime = runtime
        self.provider = provider
        self.context = ContextBuilder(settings)
        self._ingest_slots = threading.BoundedSemaphore(max(1, settings.rag_max_concurrent_ingest))
        self._system = settings.rag_system_prompt or DEFAULT_RAG_SYSTEM_PROMPT

    def ingest(self, user_id: int, document_id: int, version: int, file_name: str, file_type: str, data: bytes) -> ProcessResult:
        self.runtime.require()
        s = self.settings
        if len(data) > s.rag_max_document_bytes:
            raise RagError(413, "DOCUMENT_TOO_LARGE", "This document is too large to process.")
        if not self._ingest_slots.acquire(timeout=15):
            raise RagError(503, "RAG_BUSY", "Document processing is busy. Please try again in a moment.")
        started = time.monotonic()
        log.info("Document processing started documentId=%d version=%d type=%s bytes=%d", document_id, version, file_type, len(data))
        try:
            pages = extract_pages(file_type, data, s.rag_max_pages, s.rag_max_document_chars)
            chunks = chunk_pages(pages, s.rag_chunk_size, s.rag_chunk_overlap, s.rag_min_chunk_chars)
            if not chunks:
                raise RagError(422, "EMPTY_DOCUMENT", "This document contains no readable text.")
            vectors: list[list[float]] = []
            batch = max(1, s.embedding_batch_size)
            for i in range(0, len(chunks), batch):
                vectors.extend(self.runtime.embedder.embed_passages([c.text for c in chunks[i:i + batch]]))
            self.runtime.store.upsert(build_points(user_id, document_id, version, file_name, chunks, vectors))
            self.runtime.store.delete(build_filter(user_id, [document_id], exclude_version=version))
            log.info("Document processing completed documentId=%d version=%d chunks=%d pages=%d ms=%d", document_id, version,
                     len(chunks), len(pages), (time.monotonic() - started) * 1000)
            return ProcessResult(chunkCount=len(chunks), pageCount=len(pages), processingVersion=version)
        except Exception as e:
            code = e.code if isinstance(e, AIError) else "PROCESSING_FAILED"
            log.warning("Document processing failed documentId=%d version=%d code=%s", document_id, version, code)
            self._cleanup_partial(user_id, document_id, version)
            if isinstance(e, AIError):
                raise
            log.exception("Unexpected document processing failure documentId=%d", document_id)
            raise RagError(500, "PROCESSING_FAILED", "The document could not be processed.") from None
        finally:
            self._ingest_slots.release()

    def _cleanup_partial(self, user_id: int, document_id: int, version: int) -> None:
        """A failed run must not leave half a document behind (best effort; a reprocess/delete removes any remainder)."""
        try:
            if self.runtime.status == "ready":
                self.runtime.store.delete(build_filter(user_id, [document_id], only_version=version))
        except Exception:
            log.warning("Could not clean partial vectors documentId=%d version=%d", document_id, version)

    def delete_document(self, user_id: int, document_id: int) -> None:
        """All versions of the document, restricted to the owner."""
        self.runtime.require()
        self.runtime.store.delete(build_filter(user_id, [document_id]))
        log.info("Document vectors deleted documentId=%d", document_id)

    async def _retrieve(self, req: RagChatRequest):
        question = req.message.strip()
        if not question:
            raise AIError(400, "INVALID_REQUEST", "Message must not be empty.")
        if len(question) > self.settings.rag_max_query_length:
            raise AIError(400, "MESSAGE_TOO_LONG", f"Question is too long (max {self.settings.rag_max_query_length} characters).")
        await asyncio.to_thread(self.runtime.require)
        s = self.settings
        vector = await asyncio.to_thread(self.runtime.embedder.embed_query, question)
        hits = await asyncio.to_thread(self.runtime.store.search, vector, build_filter(req.userId, req.documentIds),
                                       s.rag_top_k, s.similarity_threshold)
        chunks = select_context(hits, s.rag_max_context_chunks, s.rag_max_context_chars, s.similarity_threshold)
        log.info("RAG query retrieved=%d used=%d", len(hits), len(chunks))
        return chunks

    def _messages(self, req: RagChatRequest, chunks) -> list[dict[str, str]]:
        messages = self.context.build(req.message, req.history)
        messages[-1] = {"role": "user", "content": wrap_question(format_context(chunks), messages[-1]["content"])}
        return messages

    @staticmethod
    def _sources(chunks) -> list[SourceModel]:
        return [SourceModel(**s.as_dict()) for s in build_sources(chunks)]

    async def answer(self, req: RagChatRequest) -> RagChatResponse:
        chunks = await self._retrieve(req)
        model = self.provider.model
        if not chunks:
            return RagChatResponse(response=NOT_FOUND_TEXT, model=model, grounded=False, sources=[])
        text = await self.provider.complete(self._system, self._messages(req, chunks))
        if is_no_answer(text):
            return RagChatResponse(response=NOT_FOUND_TEXT, model=model, grounded=False, sources=[])
        return RagChatResponse(response=text, model=model, grounded=True, sources=self._sources(chunks))

    async def stream(self, req: RagChatRequest) -> AsyncIterator[tuple[str, dict]]:
        """Awaits retrieval AND the first visible text before returning, so a failure to start is a normal HTTP error.
        Yields (event, data): delta..., sources, done  (or error)."""
        chunks = await self._retrieve(req)
        model = self.provider.model

        async def not_found() -> AsyncIterator[tuple[str, dict]]:
            yield "delta", {"text": NOT_FOUND_TEXT}
            yield "sources", {"sources": []}
            yield "done", {"model": model, "grounded": False}

        if not chunks:
            return not_found()

        it = self.provider.stream(self._system, self._messages(req, chunks))
        gate = StreamGate()
        pending: list[str] = []
        ended = False
        try:
            while True:
                try:
                    chunk = await anext(it)
                except StopAsyncIteration:
                    ended = True
                    tail = gate.finish()
                    if tail:
                        pending.append(tail)
                    break
                visible = gate.feed(chunk)
                if visible:
                    pending.append(visible)
                    break
                if gate.no_answer:
                    break
        except BaseException:
            await it.aclose()
            raise
        if gate.no_answer:
            await it.aclose()
            return not_found()
        if ended and not pending:
            await it.aclose()
            raise AIError(502, "LLM_ERROR", "The AI returned an empty answer.")

        sources = [s.model_dump(exclude_none=True) for s in self._sources(chunks)]

        async def events() -> AsyncIterator[tuple[str, dict]]:
            try:
                for text in pending:
                    yield "delta", {"text": text}
                if not ended:
                    async for chunk in it:
                        yield "delta", {"text": chunk}
                yield "sources", {"sources": sources}
                yield "done", {"model": model, "grounded": True}
            except AIError as e:
                yield "error", {"code": e.code, "message": e.message}
            finally:
                await it.aclose()

        return events()
