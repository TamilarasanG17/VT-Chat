"""Text extraction for knowledge documents (PDF, TXT, MD, DOCX, DOC) and text cleaning.

Nothing here executes document content. DOCX is read with the standard library (zipfile + XML, with size limits);
PDF needs `pypdf`; legacy .doc needs the `antiword` or `catdoc` program (otherwise a clear "save as .docx" error).
OCR is NOT implemented: a PDF without a text layer fails with NO_TEXT.
"""
from __future__ import annotations

import io
import re
import shutil
import subprocess
import tempfile
import unicodedata
import zipfile
import xml.etree.ElementTree as ET

from app.services.chunking_service import Page
from app.services.rag_errors import RagError

SUPPORTED = {"pdf", "txt", "md", "docx", "doc"}
_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
_MAX_DOCX_XML = 40 * 1024 * 1024
_MIN_TEXT_CHARS = 20

_CONTROL = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f\x7f­​﻿]")


def clean_text(text: str) -> str:
    """Conservative clean-up: normalises line breaks and whitespace, keeps headings, paragraphs and code indentation."""
    text = unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))
    text = _CONTROL.sub("", text.replace(" ", " "))
    lines = []
    for line in text.split("\n"):
        line = line.rstrip()
        line = re.sub(r"(?<=\S)[ \t]{2,}", " ", line)
        lines.append(line)
    text = "\n".join(lines)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def _decode(data: bytes) -> str:
    if b"\x00" in data[:8192] and not data.startswith((b"\xff\xfe", b"\xfe\xff")):
        raise RagError(422, "UNSUPPORTED_CONTENT", "This file does not look like a text document.")
    for enc in ("utf-8-sig", "utf-16"):
        try:
            if enc == "utf-16" and not data.startswith((b"\xff\xfe", b"\xfe\xff")):
                continue
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    try:
        return data.decode("cp1252")
    except UnicodeDecodeError:
        return data.decode("latin-1")


def _paragraph_text(p: ET.Element) -> str:
    parts: list[str] = []
    for node in p.iter():
        if node.tag == f"{_W}t" and node.text:
            parts.append(node.text)
        elif node.tag == f"{_W}tab":
            parts.append("\t")
        elif node.tag in (f"{_W}br", f"{_W}cr"):
            parts.append("\n")
    return "".join(parts)


def _extract_docx(data: bytes) -> list[Page]:
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            try:
                info = z.getinfo("word/document.xml")
            except KeyError:
                raise RagError(422, "CORRUPT_DOCUMENT", "This is not a valid Word (.docx) document.") from None
            if info.file_size > _MAX_DOCX_XML:
                raise RagError(422, "DOCUMENT_TOO_LARGE", "This document is too large to process.")
            xml = z.read(info)
    except zipfile.BadZipFile:
        raise RagError(422, "CORRUPT_DOCUMENT", "This is not a valid Word (.docx) document.") from None
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        raise RagError(422, "CORRUPT_DOCUMENT", "This Word document could not be read.") from None
    body = root.find(f"{_W}body")
    if body is None:
        return []
    blocks: list[str] = []

    def paragraph(p: ET.Element) -> str:
        text = _paragraph_text(p).strip()
        if not text:
            return ""
        ppr = p.find(f"{_W}pPr")
        if ppr is not None:
            style = ppr.find(f"{_W}pStyle")
            val = (style.get(f"{_W}val") if style is not None else "") or ""
            m = re.match(r"(?i)heading\s*([1-6])", val)
            if m:
                return "#" * int(m.group(1)) + " " + text
            if val.lower() == "title":
                return "# " + text
            if ppr.find(f"{_W}numPr") is not None:
                return "- " + text
        return text

    for child in body:
        if child.tag == f"{_W}p":
            t = paragraph(child)
            if t:
                blocks.append(t)
        elif child.tag == f"{_W}tbl":
            rows = []
            for tr in child.iter(f"{_W}tr"):
                cells = [" ".join(_paragraph_text(p).strip() for p in tc.iter(f"{_W}p")).strip() for tc in tr.findall(f"{_W}tc")]
                if any(cells):
                    rows.append(" | ".join(cells))
            if rows:
                blocks.append("\n".join(rows))
    return [Page(None, "\n\n".join(blocks))]


def _extract_doc(data: bytes) -> list[Page]:
    tool = shutil.which("antiword") or shutil.which("catdoc")
    if not tool:
        raise RagError(422, "UNSUPPORTED_DOC", "Old .doc files are not supported on this server. Please save the file as .docx and upload it again.")
    with tempfile.NamedTemporaryFile(suffix=".doc") as f:
        f.write(data)
        f.flush()
        try:
            out = subprocess.run([tool, f.name], capture_output=True, timeout=60, check=False)
        except (subprocess.TimeoutExpired, OSError):
            raise RagError(422, "CORRUPT_DOCUMENT", "This Word document could not be read.") from None
    if out.returncode != 0:
        raise RagError(422, "CORRUPT_DOCUMENT", "This Word document could not be read.")
    return [Page(None, out.stdout.decode("utf-8", "replace"))]


def _extract_pdf(data: bytes, max_pages: int) -> list[Page]:
    try:
        from pypdf import PdfReader
        from pypdf.errors import PdfReadError
    except ImportError:
        raise RagError(503, "RAG_UNAVAILABLE", "PDF support is not installed on the AI service (pip install pypdf).") from None
    try:
        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted:
            try:
                if not reader.decrypt(""):
                    raise RagError(422, "ENCRYPTED_PDF", "This PDF is password-protected.")
            except RagError:
                raise
            except Exception:
                raise RagError(422, "ENCRYPTED_PDF", "This PDF is password-protected.") from None
        total = len(reader.pages)
        if total > max_pages:
            raise RagError(422, "TOO_MANY_PAGES", f"This PDF has too many pages (maximum {max_pages}).")
        pages: list[Page] = []
        failed = 0
        for number, page in enumerate(reader.pages, start=1):
            try:
                pages.append(Page(number, page.extract_text() or ""))
            except Exception:
                failed += 1
                pages.append(Page(number, ""))
        if total and failed > total / 2:
            raise RagError(422, "CORRUPT_DOCUMENT", "This PDF could not be read.")
        return pages
    except RagError:
        raise
    except PdfReadError:
        raise RagError(422, "CORRUPT_DOCUMENT", "This PDF could not be read.") from None
    except Exception:
        raise RagError(422, "CORRUPT_DOCUMENT", "This PDF could not be read.") from None


def extract_pages(file_type: str, data: bytes, max_pages: int = 1000, max_chars: int = 3_000_000) -> list[Page]:
    """Extracted and cleaned pages. Raises RagError (NO_TEXT, EMPTY_DOCUMENT, ...) when nothing usable is found."""
    ft = (file_type or "").lower().lstrip(".")
    if ft not in SUPPORTED:
        raise RagError(422, "UNSUPPORTED_TYPE", "This file type is not supported.")
    if ft == "pdf":
        pages = _extract_pdf(data, max_pages)
    elif ft == "docx":
        pages = _extract_docx(data)
    elif ft == "doc":
        pages = _extract_doc(data)
    else:
        pages = [Page(None, _decode(data))]

    cleaned = [Page(p.number, clean_text(p.text)) for p in pages]
    cleaned = [p for p in cleaned if p.text]
    total = sum(len(p.text) for p in cleaned)
    if total < _MIN_TEXT_CHARS:
        if ft == "pdf":
            raise RagError(422, "NO_TEXT", "No readable text was found. Scanned or image-only PDFs are not supported (no OCR).")
        raise RagError(422, "EMPTY_DOCUMENT", "This document contains no readable text.")
    if total > max_chars:
        raise RagError(422, "DOCUMENT_TOO_LARGE", "This document contains too much text to process.")
    return cleaned
