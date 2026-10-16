"""Orchestrates one translation: detect script/language -> Thanglish normalization -> IndicTrans2.

The engine is an interface so the routing logic can be tested without downloading any model.
"""
from __future__ import annotations

import logging
import threading
from typing import Protocol

from app.config import Settings
from app.models.translation_models import TranslateResponse
from app.services import language_service as langs
from app.services import tanglish_service as tanglish
from app.services.language_service import Direction, Script

log = logging.getLogger("translation")


class TranslationError(Exception):
    """An error whose message is safe to show to the caller."""

    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


class Engine(Protocol):
    def is_ready(self) -> bool: ...

    def translate(self, text: str, source: str, target: str) -> str: ...


class IndicTrans2Engine:
    """IndicTrans2 checkpoints, loaded once at startup and kept in memory (one per direction)."""

    def __init__(self, settings: Settings):
        self._settings = settings
        self._models: dict[Direction, tuple] = {}
        self._processor = None
        self._device = "cpu"
        self._lock = threading.Lock()
        self._ready = False

    def is_ready(self) -> bool:
        return self._ready

    def _checkpoint(self, direction: Direction) -> str:
        s = self._settings
        if s.single_model:
            return s.single_model
        return {Direction.EN_INDIC: s.model_en_indic, Direction.INDIC_EN: s.model_indic_en,
                Direction.INDIC_INDIC: s.model_indic_indic}[direction]

    def load(self) -> None:
        import torch
        from IndicTransToolkit.processor import IndicProcessor
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        self._device = ("cuda" if torch.cuda.is_available() else "cpu") if self._settings.device == "auto" else self._settings.device
        self._processor = IndicProcessor(inference=True)
        loaded: dict[str, tuple] = {}
        for direction in Direction:
            name = self._checkpoint(direction)
            if name not in loaded:
                log.info("Loading IndicTrans2 model %s on %s", name, self._device)
                tok = AutoTokenizer.from_pretrained(name, trust_remote_code=True)
                model = AutoModelForSeq2SeqLM.from_pretrained(name, trust_remote_code=True)
                model.to(self._device).eval()
                loaded[name] = (tok, model)
            self._models[direction] = loaded[name]
        self._ready = True
        log.info("IndicTrans2 ready")

    def translate(self, text: str, source: str, target: str) -> str:
        import torch

        if not self._ready:
            raise TranslationError(503, "MODEL_NOT_READY", "Translation model is not ready yet.")
        direction = langs.direction_for(source, target)
        tokenizer, model = self._models[direction]
        src, tgt = langs.LANGUAGES[source].flores, langs.LANGUAGES[target].flores
        batch = self._processor.preprocess_batch([text], src_lang=src, tgt_lang=tgt)
        inputs = tokenizer(batch, truncation=True, padding="longest", return_tensors="pt", return_attention_mask=True).to(self._device)
        with self._lock, torch.no_grad():
            out = model.generate(**inputs, use_cache=True, min_length=0, max_length=self._settings.max_new_tokens,
                                 num_beams=self._settings.num_beams, num_return_sequences=1)
        decoded = tokenizer.batch_decode(out, skip_special_tokens=True, clean_up_tokenization_spaces=True)
        return self._processor.postprocess_batch(decoded, lang=tgt)[0]


class TranslationService:
    def __init__(self, engine: Engine):
        self._engine = engine

    def is_ready(self) -> bool:
        return self._engine.is_ready()

    def translate(self, text: str, target: str) -> TranslateResponse:
        target = (target or "").strip().lower()
        if not langs.is_supported(target):
            raise TranslationError(422, "UNSUPPORTED_LANGUAGE", "Target language is not supported.")
        if not text or not text.strip():
            raise TranslationError(422, "EMPTY_TEXT", "Nothing to translate.")

        script = langs.detect_script(text)
        if script is Script.NONE:
            return TranslateResponse(sourceLanguage=None, sourceScript=script.value, romanized=False,
                                     targetLanguage=target, translatedText=text)
        if script is Script.OTHER:
            raise TranslationError(422, "UNSUPPORTED_LANGUAGE", "The language of this message is not supported.")

        romanized = False
        if script is Script.LATIN:
            if tanglish.detect_romanized_tamil(text) is not None:
                source, romanized, script_name = "ta", True, "romanized"
            else:
                source, script_name = "en", Script.LATIN.value
        else:
            source, script_name = langs.SCRIPT_LANGUAGE[script], script.value

        if source == target and not romanized:
            return TranslateResponse(sourceLanguage=source, sourceScript=script_name, romanized=False,
                                     targetLanguage=target, translatedText=text)

        working = tanglish.transliterate_to_tamil(text) if romanized else text
        if romanized and target == "ta":
            translated = working
        else:
            translated = self._engine.translate(working, source, target)
        return TranslateResponse(sourceLanguage=source, sourceScript=script_name, romanized=romanized,
                                 targetLanguage=target, translatedText=translated)
