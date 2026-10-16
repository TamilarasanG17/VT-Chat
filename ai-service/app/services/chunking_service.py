"""Splits cleaned page text into overlapping chunks. Pure Python (no third-party imports).

Rules: a chunk NEVER crosses a page boundary (so a citation's page number is exact); paragraphs are the preferred
split points, then lines, then sentences, then words; consecutive chunks of a page overlap by about `overlap`
characters; a tiny trailing chunk is merged into the previous one (no 20-character chunks); fenced code blocks are
kept whole when they fit.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

_PARA = re.compile(r"\n\s*\n")
_SENTENCE = re.compile(r"(?<=[.!?।॥])\s+")


@dataclass(frozen=True)
class Page:
    number: int | None
    text: str


@dataclass(frozen=True)
class Chunk:
    index: int
    page: int | None
    text: str


def _hard_split(text: str, size: int) -> list[str]:
    out: list[str] = []
    rest = text.strip()
    while len(rest) > size:
        cut = rest.rfind(" ", 0, size)
        if cut < size // 2:
            cut = size
        out.append(rest[:cut].strip())
        rest = rest[cut:].strip()
    if rest:
        out.append(rest)
    return out


def _atoms(text: str, size: int) -> list[tuple[str, str]]:
    """(separator-before, text) pieces, each at most `size` characters."""
    atoms: list[tuple[str, str]] = []
    for para in _PARA.split(text):
        para = para.strip("\n")
        if not para.strip():
            continue
        sep = "\n\n"
        if len(para) <= size:
            atoms.append((sep, para))
            continue
        if "```" in para:
            units = [(l, "\n") for l in para.split("\n")]
        else:
            units = []
            for line in para.split("\n"):
                if len(line) <= size:
                    units.append((line, "\n"))
                else:
                    for sentence in _SENTENCE.split(line):
                        if len(sentence) <= size:
                            units.append((sentence, " "))
                        else:
                            units.extend((w, " ") for w in _hard_split(sentence, size))
        first = True
        for unit, unit_sep in units:
            if not unit.strip():
                continue
            atoms.append((sep if first else unit_sep, unit))
            first = False
    return atoms


def _tail_overlap(atoms: list[tuple[str, str]], overlap: int) -> list[tuple[str, str]]:
    """The trailing atoms (whole pieces) that fit in `overlap` characters; empty if none fits."""
    kept: list[tuple[str, str]] = []
    total = 0
    for sep, text in reversed(atoms):
        if total + len(text) > overlap:
            break
        kept.append((sep, text))
        total += len(text) + len(sep)
    kept.reverse()
    return kept


def _render(group: list[tuple[str, str]]) -> str:
    return "".join((sep if i else "") + text for i, (sep, text) in enumerate(group)).strip()


def split_text(text: str, size: int, overlap: int, min_chars: int) -> list[str]:
    atoms = _atoms(text, size)
    groups: list[tuple[list[tuple[str, str]], int]] = []
    current: list[tuple[str, str]] = []
    carried = 0
    for sep, piece in atoms:
        length = len(_render(current)) if current else 0
        add = len(piece) + (len(sep) if current else 0)
        if current and length + add > size:
            groups.append((current, carried))
            carry = _tail_overlap(current, overlap) if overlap else []
            if overlap and not carry:
                tail = _render(current)[-overlap:]
                space = tail.find(" ")
                tail = tail[space + 1:].strip() if 0 <= space < len(tail) - 1 else tail.strip()
                carry = [("\n\n", tail)] if tail else []
            if len(carry) == len(current) or len(_render(carry)) + len(sep) + len(piece) > size:
                carry = []
            current = list(carry)
            carried = len(carry)
        current.append((sep, piece))
    if current:
        groups.append((current, carried))

    texts = [_render(g) for g, _ in groups]
    if len(texts) > 1 and len(texts[-1]) < min_chars:
        tail, carried_n = groups[-1]
        new_atoms = tail[carried_n:]
        merged = texts[-2] + ("\n" + _render(new_atoms) if new_atoms else "")
        texts[-2] = merged.strip()
        texts.pop()
    return [t for t in texts if t]


def chunk_pages(pages: list[Page], size: int, overlap: int, min_chars: int) -> list[Chunk]:
    chunks: list[Chunk] = []
    for page in pages:
        for text in split_text(page.text, size, overlap, min_chars):
            if len(re.sub(r"\W", "", text)) < 8:
                continue
            chunks.append(Chunk(index=len(chunks), page=page.number, text=text))
    return chunks
