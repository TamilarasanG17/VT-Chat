# VTChat AI service (Phase 11)

A small FastAPI service that talks to an LLM. **Nothing else**: no users, no login, no database, no translation.
Spring Boot is its only client and authenticates with a shared key; the browser never reaches it.

```
React  ->  Spring Boot (/api/ai/*)  ->  this service (/ai/chat, /ai/chat/stream)  ->  LLM provider
```

The translation service (`translation-service/`, IndicTrans2, port 8001) is a different service and is not touched.

## Run

```bash
cd ai-service
python -m venv .venv && source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                                     # then edit .env
python -m app                                            # or: uvicorn app.main:app --port 8002
```

Out of the box `LLM_PROVIDER=mock` answers with a canned reply (no key, no network), so you can run and try the whole
application first. Then switch to a real model **by editing `.env` only**:

| `LLM_PROVIDER` | Protocol | Default base URL | Needs `LLM_API_KEY` |
|---|---|---|---|
| `openai` | OpenAI chat completions | `https://api.openai.com/v1` | yes |
| `groq` | OpenAI-compatible | `https://api.groq.com/openai/v1` | yes |
| `openrouter` | OpenAI-compatible | `https://openrouter.ai/api/v1` | yes |
| `gemini` | OpenAI-compatible endpoint | `https://generativelanguage.googleapis.com/v1beta/openai` | yes |
| `ollama` | OpenAI-compatible (local) | `http://localhost:11434/v1` | no |
| `openai-compatible` | vLLM, LM Studio, gateways... | set `LLM_BASE_URL` | optional |
| `anthropic` | Anthropic Messages API | `https://api.anthropic.com` | yes |
| `mock` | none | - | no |

Example: `LLM_PROVIDER=groq`, `LLM_MODEL=<a model id from your provider>`, `LLM_API_KEY=...`. Restart the service. Spring Boot
and React do not change. Model ids change often; use one from your provider's current list.

## API (internal, header `X-Internal-Api-Key` required)

| Endpoint | |
|---|---|
| `GET /health` | `{status, provider, model}` (no secrets) |
| `POST /ai/chat` | `{message, conversationId?, history:[{role:user\|assistant, content}]}` -> `{response, model}` |
| `POST /ai/chat/stream` | same request, answer as Server-Sent Events: `delta {text}`, `done {model}`, `error {code,message}` |

Errors are `{success:false, code, message}` with these codes: `UNAUTHORIZED`, `INVALID_REQUEST`, `MESSAGE_TOO_LONG`,
`CONTEXT_TOO_LARGE`, `LLM_UNAVAILABLE`, `LLM_TIMEOUT`, `LLM_RATE_LIMITED` (+`retryAfterSeconds`), `LLM_AUTH` (bad provider
key), `LLM_ERROR`. Messages are fixed, user-safe texts: provider response bodies, URLs, keys and stack traces are never returned.
With `LLM_PROVIDER=mock`, start a message with `[[fail:timeout]]`, `[[fail:unavailable]]`, `[[fail:auth]]`, `[[fail:busy]]` or
`[[fail:context]]` to simulate a provider failure.

## What it enforces

* **System prompt** is configured here (`AI_SYSTEM_PROMPT` / `AI_SYSTEM_PROMPT_FILE`, default in `app/config/settings.py`).
  A request cannot change it: history roles other than `user`/`assistant` are rejected.
* **Context** (`app/services/context_service.py`): at most `AI_MAX_CONTEXT_MESSAGES` history messages (newest, in order) and
  `AI_MAX_CONTEXT_CHARS` characters in total; starts with a user turn; consecutive same-role messages are merged (what a
  failed generation leaves behind). A message longer than `AI_MAX_MESSAGE_LENGTH` is rejected.
* **Timeouts**: `LLM_TIMEOUT_MS` (connect / first byte / idle between chunks; the whole non-streamed call) and
  `AI_STREAM_MAX_MS` (one whole streamed answer). **No automatic retries** (an LLM call costs money).
* **Cancellation**: when Spring Boot closes the stream (the user pressed Stop), the generator is closed and the provider
  HTTP request is closed with it.
* **Stateless**: nothing is stored here; restart it any time. Conversations live in MySQL behind Spring Boot.
* **Logging**: request id (`X-Request-Id`, also logged by Spring Boot), provider/model, counts, durations, error codes.
  Never prompts, answers, keys or tokens. The `httpx` logger is silenced so URLs stay out of the log.

## Tests

`python -m unittest discover -s tests -t .` (or `pytest`). They cover the context rules, error mapping, settings validation
and the mock provider and need no network or API key.

## Phase 12: RAG ("My Knowledge")

Spring Boot sends a stored document here (`POST /rag/documents/process`, raw bytes), and later asks questions
(`POST /rag/chat`, `POST /rag/chat/stream`). All `/rag/*` routes need the same `X-API-Key` and are never called by the browser.

* **Pipeline** (`document_service` -> `chunking_service` -> `embedding_service` -> `vector/qdrant_service`): extract text
  (PDF via `pypdf`, DOCX via the standard library, TXT/MD with encoding detection, DOC via `antiword`/`catdoc` if installed),
  clean, chunk (`RAG_CHUNK_SIZE`/`RAG_CHUNK_OVERLAP`, per page, so page numbers stay accurate), embed in batches, upsert.
  **No OCR**: an image-only PDF fails with `NO_TEXT`.
* **Embeddings** use a dedicated model (`EMBEDDING_PROVIDER`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSION`), never the chat LLM.
  The collection is created with that size; a collection of a different size is refused (`VECTOR_STORE_MISMATCH`).
  `mock` is keyword-like, **not semantic**, and exists so the plumbing runs without downloads.
* **Isolation**: every Qdrant search/delete carries a mandatory `userId` filter (and optional `documentId` list) executed
  inside Qdrant. `userId` comes from Spring Boot's authenticated principal, never from the client.
* **Versioned ingestion**: each (re)process has a `processingVersion`; new vectors are written first, older versions deleted
  afterwards, and a failed run deletes its own partial vectors.
* **Grounded answers**: retrieved excerpts are placed in a delimited block labelled as untrusted data; if nothing relevant is
  found (or the model answers with the `[[NO_ANSWER_IN_DOCUMENTS]]` marker) the fixed "couldn't find" text is returned.
  Sources are built only from the chunks actually sent to the model.
* **Failure isolation**: Qdrant/embedding trouble returns `503 RAG_UNAVAILABLE`; normal `/ai/chat` is unaffected.
  `/health` shows `rag` status. Logs contain ids, counts and timings, never document text, prompts or keys.

Limits worth knowing: retrieval uses the current question only (a very short follow-up may retrieve poorly); e5 models
truncate at ~512 tokens, so for Tamil/Hindi consider a smaller `RAG_CHUNK_SIZE`; tune `RAG_SIMILARITY_THRESHOLD` for your model.
