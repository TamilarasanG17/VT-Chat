"""Embedding providers. A dedicated embedding model is used (never the chat LLM).

* local              sentence-transformers, e.g. intfloat/multilingual-e5-small (384 dims; English, Tamil, Hindi,
                     Malayalam, Kannada and ~90 other languages). Loaded ONCE and kept in memory.
* openai-compatible  POST {EMBEDDING_BASE_URL}/embeddings (OpenAI, Ollama, vLLM, ... any server that offers embeddings).
* mock               deterministic hashed bag-of-words vectors: no download, no network. Lets you run and test the
                     whole pipeline; retrieval is keyword-like, NOT semantic. Use `local` for real use.

Every returned vector is checked against EMBEDDING_DIMENSION, which must equal the Qdrant collection's vector size.
"""
from __future__ import annotations

import hashlib
import math
import re
import threading

from app.config import Settings
from app.services.rag_errors import RagError

_TOKEN = re.compile(r"\w+", re.UNICODE)


def _l2(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


class EmbeddingProvider:
    name = "base"
    model = ""
    dimension = 0

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        raise NotImplementedError

    def embed_query(self, text: str) -> list[float]:
        raise NotImplementedError

    def check(self, vectors: list[list[float]]) -> list[list[float]]:
        for v in vectors:
            if len(v) != self.dimension:
                raise RagError(503, "EMBEDDING_DIMENSION_MISMATCH",
                               "The embedding model returned a different vector size than EMBEDDING_DIMENSION.")
        return vectors


class MockEmbedding(EmbeddingProvider):
    name = "mock"

    def __init__(self, settings: Settings):
        self.model = "mock-hash"
        self.dimension = settings.embedding_dimension

    def _vec(self, text: str) -> list[float]:
        vec = [0.0] * self.dimension
        for token in _TOKEN.findall(text.lower()):
            h = int.from_bytes(hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest(), "big")
            vec[h % self.dimension] += 1.0
        return _l2(vec)

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        return self.check([self._vec(t) for t in texts])

    def embed_query(self, text: str) -> list[float]:
        return self.check([self._vec(text)])[0]


class LocalEmbedding(EmbeddingProvider):
    name = "local"

    def __init__(self, settings: Settings):
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError:
            raise RagError(503, "RAG_UNAVAILABLE",
                           "sentence-transformers is not installed (pip install -r requirements-embeddings.txt).") from None
        self.model = settings.embedding_model
        self._batch = settings.embedding_batch_size
        e5 = "e5" in self.model.lower()
        self._qp = settings.embedding_query_prefix if settings.embedding_query_prefix is not None else ("query: " if e5 else "")
        self._pp = settings.embedding_passage_prefix if settings.embedding_passage_prefix is not None else ("passage: " if e5 else "")
        self._model = SentenceTransformer(self.model)
        self.dimension = int(self._model.get_sentence_embedding_dimension())
        if self.dimension != settings.embedding_dimension:
            raise RagError(503, "EMBEDDING_DIMENSION_MISMATCH",
                           f"EMBEDDING_DIMENSION={settings.embedding_dimension} but {self.model} produces {self.dimension}.")
        self._lock = threading.Lock()

    def _encode(self, texts: list[str]) -> list[list[float]]:
        with self._lock:
            arr = self._model.encode(texts, batch_size=self._batch, normalize_embeddings=True, show_progress_bar=False)
        return self.check([list(map(float, row)) for row in arr])

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        return self._encode([self._pp + t for t in texts])

    def embed_query(self, text: str) -> list[float]:
        return self._encode([self._qp + text])[0]


class OpenAICompatibleEmbedding(EmbeddingProvider):
    name = "openai-compatible"

    def __init__(self, settings: Settings):
        import httpx
        self.model = settings.embedding_model
        self.dimension = settings.embedding_dimension
        self._batch = max(1, settings.embedding_batch_size)
        self._qp = settings.embedding_query_prefix or ""
        self._pp = settings.embedding_passage_prefix or ""
        headers = {"Content-Type": "application/json"}
        if settings.embedding_api_key:
            headers["Authorization"] = f"Bearer {settings.embedding_api_key}"
        self._client = httpx.Client(base_url=settings.embedding_base_url, headers=headers,
                                    timeout=httpx.Timeout(settings.embedding_timeout_ms / 1000, connect=10))
        self._httpx = httpx

    def _embed(self, texts: list[str]) -> list[list[float]]:
        out: list[list[float]] = []
        for i in range(0, len(texts), self._batch):
            batch = texts[i:i + self._batch]
            try:
                resp = self._client.post("/embeddings", json={"model": self.model, "input": batch})
            except self._httpx.TimeoutException:
                raise RagError(503, "EMBEDDING_FAILED", "The embedding service timed out.") from None
            except self._httpx.TransportError:
                raise RagError(503, "EMBEDDING_FAILED", "The embedding service is not reachable.") from None
            if resp.status_code >= 400:
                raise RagError(503, "EMBEDDING_FAILED", "The embedding service rejected the request.")
            try:
                data = sorted(resp.json()["data"], key=lambda d: d.get("index", 0))
                out.extend([list(map(float, d["embedding"])) for d in data])
            except (KeyError, ValueError, TypeError):
                raise RagError(503, "EMBEDDING_FAILED", "The embedding service returned an unreadable answer.") from None
        if len(out) != len(texts):
            raise RagError(503, "EMBEDDING_FAILED", "The embedding service returned the wrong number of vectors.")
        return self.check([_l2(v) for v in out])

    def embed_passages(self, texts: list[str]) -> list[list[float]]:
        return self._embed([self._pp + t for t in texts])

    def embed_query(self, text: str) -> list[float]:
        return self._embed([self._qp + text])[0]


def create_embedding_provider(settings: Settings) -> EmbeddingProvider:
    if settings.embedding_provider == "mock":
        return MockEmbedding(settings)
    if settings.embedding_provider == "local":
        return LocalEmbedding(settings)
    return OpenAICompatibleEmbedding(settings)
