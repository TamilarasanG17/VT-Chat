"""Qdrant over its REST API (httpx). Only this service talks to Qdrant: never the browser, never Spring Boot.

Security: EVERY search and delete is built by `build_filter`, which always starts with `userId = <the caller>`.
The user id comes from Spring Security via Spring Boot, never from the browser, so one user can never retrieve
or delete another user's vectors.
"""
from __future__ import annotations

import logging
import uuid
from dataclasses import dataclass

import httpx

from app.config import Settings
from app.services.rag_errors import RagError

log = logging.getLogger("ai.qdrant")

_NAMESPACE = uuid.UUID("6f1d3b4e-8c53-4d0a-9b52-5f0a8e7c2a11")
_BATCH = 64


def point_id(document_id: int, version: int, chunk_index: int) -> str:
    """Deterministic: re-ingesting the same (document, version, chunk) overwrites the same point, never duplicates it."""
    return str(uuid.uuid5(_NAMESPACE, f"{document_id}:{version}:{chunk_index}"))


def build_filter(user_id: int, document_ids: list[int] | None = None, *, exclude_version: int | None = None,
                 only_version: int | None = None) -> dict:
    must: list[dict] = [{"key": "userId", "match": {"value": int(user_id)}}]
    if document_ids is not None:
        ids = [int(d) for d in document_ids]
        must.append({"key": "documentId", "match": {"value": ids[0]} if len(ids) == 1 else {"any": ids}})
    if only_version is not None:
        must.append({"key": "processingVersion", "match": {"value": int(only_version)}})
    flt: dict = {"must": must}
    if exclude_version is not None:
        flt["must_not"] = [{"key": "processingVersion", "match": {"value": int(exclude_version)}}]
    return flt


def build_points(user_id: int, document_id: int, version: int, file_name: str,
                 chunks: list, vectors: list[list[float]]) -> list[dict]:
    """One point per chunk. The payload holds the chunk text and small metadata, not the whole document."""
    points = []
    for chunk, vector in zip(chunks, vectors, strict=True):
        pid = point_id(document_id, version, chunk.index)
        payload = {
            "userId": int(user_id),
            "documentId": int(document_id),
            "chunkId": pid,
            "chunkIndex": chunk.index,
            "fileName": file_name[:255],
            "processingVersion": int(version),
            "text": chunk.text,
        }
        if chunk.page is not None:
            payload["pageNumber"] = chunk.page
        points.append({"id": pid, "vector": vector, "payload": payload})
    return points


@dataclass(frozen=True)
class Hit:
    score: float
    document_id: int
    file_name: str
    page: int | None
    chunk_index: int
    text: str


class QdrantStore:
    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.collection = settings.qdrant_collection
        self.dimension = settings.embedding_dimension
        headers = {"Content-Type": "application/json"}
        if settings.qdrant_api_key:
            headers["api-key"] = settings.qdrant_api_key
        self._client = httpx.Client(base_url=settings.qdrant_url, headers=headers, transport=transport,
                                    timeout=httpx.Timeout(settings.qdrant_timeout_ms / 1000, connect=5))

    def _call(self, method: str, path: str, **kw):
        try:
            resp = self._client.request(method, path, **kw)
        except httpx.TimeoutException:
            log.warning("Qdrant timeout %s %s", method, path.split("?")[0])
            raise RagError(503, "VECTOR_STORE_FAILED", "The vector database timed out.") from None
        except httpx.TransportError:
            log.warning("Qdrant not reachable")
            raise RagError(503, "VECTOR_STORE_FAILED", "The vector database is not reachable.") from None
        return resp

    def _ok(self, resp, what: str):
        if resp.status_code >= 400:
            log.warning("Qdrant %s failed status=%s", what, resp.status_code)
            raise RagError(503, "VECTOR_STORE_FAILED", "The vector database rejected the request.")
        try:
            return resp.json()
        except ValueError:
            raise RagError(503, "VECTOR_STORE_FAILED", "The vector database returned an unreadable answer.") from None

    def ensure_collection(self) -> None:
        """Creates the collection with the configured dimension, or verifies an existing one matches it."""
        resp = self._call("GET", f"/collections/{self.collection}")
        if resp.status_code == 404:
            self._ok(self._call("PUT", f"/collections/{self.collection}",
                                json={"vectors": {"size": self.dimension, "distance": "Cosine"}}), "create collection")
            log.info("Created Qdrant collection %s (size=%d, cosine)", self.collection, self.dimension)
        else:
            body = self._ok(resp, "read collection")
            try:
                vectors = body["result"]["config"]["params"]["vectors"]
                size = int(vectors["size"])
            except (KeyError, TypeError, ValueError):
                raise RagError(503, "VECTOR_STORE_MISMATCH",
                               f"Qdrant collection {self.collection} uses named vectors; a single unnamed vector is required.") from None
            if size != self.dimension:
                raise RagError(503, "VECTOR_STORE_MISMATCH",
                               f"Qdrant collection {self.collection} has vector size {size} but EMBEDDING_DIMENSION is {self.dimension}. "
                               "Use a different QDRANT_COLLECTION or recreate the collection.")
        for field in ("userId", "documentId", "processingVersion"):
            self._call("PUT", f"/collections/{self.collection}/index?wait=true", json={"field_name": field, "field_schema": "integer"})

    def ping(self) -> bool:
        try:
            return self._call("GET", f"/collections/{self.collection}").status_code == 200
        except RagError:
            return False

    def upsert(self, points: list[dict]) -> None:
        for i in range(0, len(points), _BATCH):
            self._ok(self._call("PUT", f"/collections/{self.collection}/points?wait=true",
                                json={"points": points[i:i + _BATCH]}), "upsert")

    def delete(self, flt: dict) -> None:
        self._ok(self._call("POST", f"/collections/{self.collection}/points/delete?wait=true", json={"filter": flt}), "delete")

    def count(self, flt: dict) -> int:
        body = self._ok(self._call("POST", f"/collections/{self.collection}/points/count", json={"filter": flt, "exact": True}), "count")
        return int(body["result"]["count"])

    def search(self, vector: list[float], flt: dict, limit: int, threshold: float) -> list[Hit]:
        body: dict = {"vector": vector, "filter": flt, "limit": limit, "with_payload": True}
        if threshold > 0:
            body["score_threshold"] = threshold
        data = self._ok(self._call("POST", f"/collections/{self.collection}/points/search", json=body), "search")
        hits: list[Hit] = []
        for r in data.get("result", []):
            p = r.get("payload") or {}
            try:
                hits.append(Hit(score=float(r["score"]), document_id=int(p["documentId"]), file_name=str(p.get("fileName", "")),
                                page=int(p["pageNumber"]) if p.get("pageNumber") is not None else None,
                                chunk_index=int(p.get("chunkIndex", 0)), text=str(p.get("text", ""))))
            except (KeyError, TypeError, ValueError):
                continue
        return hits

    def close(self) -> None:
        self._client.close()
