# VTChat translation service

Python FastAPI microservice that does **translation only** (IndicTrans2). It has no auth, users, messages, WebSockets,
MySQL or media. Only the Spring Boot backend calls it; the browser never does.

```
React -> Spring Boot (TranslationService: auth, membership, cache) -> this service -> IndicTrans2
```

## Setup (own virtualenv - keep it separate from the Java build)

```bash
cd translation-service
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                 # set TRANSLATION_SERVICE_API_KEY
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

The first start downloads the IndicTrans2 checkpoints from Hugging Face (needs internet and a few GB of disk); they are
cached afterwards and loaded **once** into memory at startup. `GET /health` reports `modelReady`.

## Environment

| Variable | Meaning |
|---|---|
| `TRANSLATION_SERVICE_HOST` / `_PORT` | bind address (default 0.0.0.0:8001) |
| `TRANSLATION_SERVICE_API_KEY` | shared secret, required (service refuses to start without it) |
| `INDICTRANS2_MODEL` | optional single checkpoint used for every direction |
| `INDICTRANS2_MODEL_EN_INDIC` / `_INDIC_EN` / `_INDIC_INDIC` | per-direction checkpoints (distilled defaults) |
| `INDICTRANS2_DEVICE` | `auto`, `cpu` or `cuda` |

## API

`POST /translate` with header `X-Internal-Api-Key`:

```json
{ "text": "epdi iruka", "targetLanguage": "en" }
```
```json
{ "sourceLanguage": "ta", "sourceScript": "romanized", "romanized": true, "targetLanguage": "en", "translatedText": "How are you?" }
```

`sourceScript` is one of `latin`, `romanized`, `tamil`, `devanagari`, `malayalam`, `kannada`, `none`.
Errors are `{ "code", "message" }`: 401 bad key, 422 unsupported language / empty / invalid, 503 model loading, 500 generic. No stack traces.

## How it works

1. `language_service` - Unicode script detection, language registry (en, ta, hi, ml, kn), direction routing
   (en->indic, indic->en, indic->indic checkpoint).
2. `tanglish_service` - detects Romanized Tamil from a lexicon + sub-word patterns (not every Latin word is Tamil;
   English words stay untouched), then transliterates to Tamil script. No phrase dictionary translation.
3. `translation_service` - Tamil script goes straight to IndicTrans2; Thanglish is normalised to Tamil first; same-language text is returned unchanged.

Adding a language: add a line to `LANGUAGES` (FLORES code) and, if it has its own script, to `SCRIPT_LANGUAGE`.

## Tests

```bash
python -I -m unittest discover -s tests -t .
```
These cover detection, transliteration and routing with a fake engine (no model download).
