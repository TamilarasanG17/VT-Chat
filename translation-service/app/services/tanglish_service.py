"""Romanized Tamil (Thanglish) detection and normalization to Tamil script.

This is NOT a translator and has no phrase dictionary. It answers "is this Latin text probably Tamil?" and rewrites it
in Tamil script so IndicTrans2 can read it. English words inside the message (office, lunch, now...) are left as is.
Chat spelling is not standardised, so the transliteration is best effort.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

from app.services import lexicon

_WORD = re.compile(r"[a-z]+(?:'[a-z]+)?")
_WORD_ANY_CASE = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
_TAMIL_PATTERNS = [re.compile(p) for p in ("zh", "ndh", "ngal", "aanga", "eenga", "[ui]kku$")]
PULLI = "்"

MIN_SCORE = 1.0
MIN_COVERAGE = 0.3
ENGLISH_SHARE_STRICT = 0.6
STRICT_MIN_WORDS = 4
STRICT_SCORE = 2.0
STRICT_COVERAGE = 0.4


@dataclass(frozen=True)
class Detection:
    score: float
    coverage: float
    decisive: bool


def words(text: str) -> list[str]:
    return _WORD.findall(text.lower())


def _score(ws: list[str]) -> float:
    total = 0.0
    for w in ws:
        if w in lexicon.TAMIL_STRONG:
            total += 1.0
        elif w in lexicon.TAMIL_WEAK:
            total += 0.3
        elif len(w) >= 5 and w not in lexicon.ENGLISH:
            if any(p.search(w) for p in _TAMIL_PATTERNS):
                total += 0.4
    return total


def detect_romanized_tamil(text: str) -> Detection | None:
    """Return a Detection when the Latin text is probably Thanglish, otherwise None (treat it as English)."""
    ws = words(text)
    if not ws:
        return None
    score = _score(ws)
    coverage = score / len(ws)
    min_score, min_cov = MIN_SCORE, MIN_COVERAGE
    english_share = sum(1 for w in ws if w in lexicon.ENGLISH) / len(ws)
    if len(ws) >= STRICT_MIN_WORDS and english_share >= ENGLISH_SHARE_STRICT:
        min_score, min_cov = STRICT_SCORE, STRICT_COVERAGE
    if score < min_score or coverage < min_cov:
        return None
    return Detection(score, coverage, score >= 2.0 and coverage >= 0.6)



_VOWELS = [
    ("aa", "ஆ", "ா"), ("ai", "ஐ", "ை"), ("au", "ஔ", "ௌ"),
    ("ee", "ஈ", "ீ"), ("ii", "ஈ", "ீ"), ("oo", "ஊ", "ூ"), ("uu", "ஊ", "ூ"),
    ("a", "அ", ""), ("i", "இ", "ி"), ("u", "உ", "ு"), ("e", "எ", "ெ"), ("o", "ஒ", "ொ"),
]
_CONSONANTS = [
    ("nj", "ஞ"), ("ng", "ங"), ("zh", "ழ"), ("ch", "ச"), ("sh", "ஷ"), ("th", "த"), ("dh", "த"), ("kh", "க"),
    ("gh", "க"), ("bh", "ப"), ("ph", "ப"),
    ("k", "க"), ("g", "க"), ("s", "ச"), ("j", "ஜ"), ("t", "ட"), ("d", "ட"), ("p", "ப"), ("b", "ப"),
    ("m", "ம"), ("y", "ய"), ("r", "ர"), ("l", "ல"), ("v", "வ"), ("w", "வ"), ("h", "ஹ"), ("z", "ழ"),
    ("q", "க"), ("c", "க"), ("f", "ஃப"), ("x", "க்ஸ"), ("n", "ன"),
]


def _vowel_at(w: str, i: int):
    for v in _VOWELS:
        if w.startswith(v[0], i):
            return v
    return None


def _consonant_at(w: str, i: int):
    for c in _CONSONANTS:
        if w.startswith(c[0], i):
            return c
    return None


def _n_letter(w: str, start: int, nxt: int) -> str:
    """Tamil has three n letters; chat spelling does not say which, so use the common pattern."""
    if start == 0:
        return "ந"
    if w.startswith("th", nxt) or w.startswith("dh", nxt):
        return "ந"
    if nxt < len(w) and w[nxt] in "td":
        return "ண"
    return "ன"


def _rules(w: str) -> str:
    out: list[str] = []
    pending = False
    previous = ""
    i = 0
    while i < len(w):
        v = _vowel_at(w, i)
        if v is not None:
            out.append(v[2] if pending else v[1])
            pending, previous = False, ""
            i += len(v[0])
            continue
        c = _consonant_at(w, i)
        if c is None:
            i += 1
            continue
        tamil = c[1]
        nxt = i + len(c[0])
        if c[0] == "n":
            tamil = _n_letter(w, i, nxt)
        elif c[0] == "r" and previous in ("ன", "ர", "ற"):
            tamil = "ற"
        elif c[0] == "c":
            tamil = "ச" if nxt < len(w) and w[nxt] in "eiy" else "க"
        if pending:
            out.append(PULLI)
        out.append(tamil)
        pending, previous = True, tamil
        i = nxt
    if pending:
        out.append(PULLI)
    return "".join(out)


def _convert_word(original: str) -> str:
    w = original.lower().replace("'", "")
    spelled = lexicon.TAMIL_SPELLING.get(w)
    if spelled is not None:
        return spelled
    if len(w) <= 1 or w in lexicon.ENGLISH:
        return original
    return _rules(w)


def transliterate_to_tamil(text: str) -> str:
    """Latin Thanglish -> Tamil script. English words, punctuation, digits and spacing are kept."""
    return _WORD_ANY_CASE.sub(lambda m: _convert_word(m.group(0)), text)
