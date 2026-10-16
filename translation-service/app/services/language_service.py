"""Language registry, script detection and direction routing.

Adding a language = add one line to LANGUAGES (and, if it has its own script, to SCRIPT_LANGUAGE). IndicTrans2 supports
22 Indic languages; only the five VTChat needs are enabled by default.
"""
from __future__ import annotations

import unicodedata
from dataclasses import dataclass
from enum import Enum


@dataclass(frozen=True)
class Language:
    code: str
    flores: str
    name: str
    script: str


LANGUAGES: dict[str, Language] = {
    "en": Language("en", "eng_Latn", "English", "latin"),
    "ta": Language("ta", "tam_Taml", "Tamil", "tamil"),
    "hi": Language("hi", "hin_Deva", "Hindi", "devanagari"),
    "ml": Language("ml", "mal_Mlym", "Malayalam", "malayalam"),
    "kn": Language("kn", "kan_Knda", "Kannada", "kannada"),
}


class Script(str, Enum):
    LATIN = "latin"
    TAMIL = "tamil"
    DEVANAGARI = "devanagari"
    MALAYALAM = "malayalam"
    KANNADA = "kannada"
    OTHER = "other"
    NONE = "none"


SCRIPT_LANGUAGE = {
    Script.TAMIL: "ta",
    Script.DEVANAGARI: "hi",
    Script.MALAYALAM: "ml",
    Script.KANNADA: "kn",
}

_PREFIXES = {
    "LATIN": Script.LATIN,
    "TAMIL": Script.TAMIL,
    "DEVANAGARI": Script.DEVANAGARI,
    "MALAYALAM": Script.MALAYALAM,
    "KANNADA": Script.KANNADA,
}

NON_LATIN_SHARE = 0.4


def is_supported(code: str | None) -> bool:
    return code in LANGUAGES


def detect_script(text: str) -> Script:
    counts: dict[Script, int] = {}
    letters = 0
    for ch in text:
        if not ch.isalpha():
            continue
        letters += 1
        try:
            first = unicodedata.name(ch).split(" ", 1)[0]
        except ValueError:
            first = ""
        script = _PREFIXES.get(first, Script.OTHER)
        counts[script] = counts.get(script, 0) + 1
    if letters == 0:
        return Script.NONE
    best, best_count = None, 0
    for script, n in counts.items():
        if script is not Script.LATIN and n > best_count:
            best, best_count = script, n
    latin = counts.get(Script.LATIN, 0)
    if best is None:
        return Script.LATIN
    return best if best_count / letters >= NON_LATIN_SHARE or latin == 0 else Script.LATIN


class Direction(str, Enum):
    EN_INDIC = "en-indic"
    INDIC_EN = "indic-en"
    INDIC_INDIC = "indic-indic"


def direction_for(source: str, target: str) -> Direction:
    """Which IndicTrans2 checkpoint handles source -> target."""
    if source == "en":
        return Direction.EN_INDIC
    if target == "en":
        return Direction.INDIC_EN
    return Direction.INDIC_INDIC
