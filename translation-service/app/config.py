"""Settings from environment variables (a local .env file is loaded when python-dotenv is installed)."""
from __future__ import annotations

import os
from dataclasses import dataclass

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    api_key: str
    model_en_indic: str
    model_indic_en: str
    model_indic_indic: str
    single_model: str
    device: str
    max_new_tokens: int
    num_beams: int
    preload: bool


def load_settings() -> Settings:
    return Settings(
        host=os.getenv("TRANSLATION_SERVICE_HOST", "0.0.0.0"),
        port=int(os.getenv("TRANSLATION_SERVICE_PORT", "8001")),
        api_key=os.getenv("TRANSLATION_SERVICE_API_KEY", "").strip(),
        single_model=os.getenv("INDICTRANS2_MODEL", "").strip(),
        model_en_indic=os.getenv("INDICTRANS2_MODEL_EN_INDIC", "ai4bharat/indictrans2-en-indic-dist-200M"),
        model_indic_en=os.getenv("INDICTRANS2_MODEL_INDIC_EN", "ai4bharat/indictrans2-indic-en-dist-200M"),
        model_indic_indic=os.getenv("INDICTRANS2_MODEL_INDIC_INDIC", "ai4bharat/indictrans2-indic-indic-dist-320M"),
        device=os.getenv("INDICTRANS2_DEVICE", "auto"),
        max_new_tokens=int(os.getenv("INDICTRANS2_MAX_LENGTH", "256")),
        num_beams=int(os.getenv("INDICTRANS2_NUM_BEAMS", "5")),
        preload=os.getenv("INDICTRANS2_PRELOAD", "true").lower() != "false",
    )
