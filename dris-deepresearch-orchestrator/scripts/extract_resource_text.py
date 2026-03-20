import json
import os
import re
import sys
import tempfile
import urllib.request
from html import unescape
from urllib.parse import urlparse

MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024


def short_text(value: str, max_len: int) -> str:
    normalized = re.sub(r"\s+", " ", (value or "")).strip()
    if len(normalized) <= max_len:
        return normalized
    return normalized[: max(0, max_len - 3)] + "..."


def split_lines(text: str, limit: int):
    lines = []
    for part in re.split(r"[\r\n]+", text or ""):
        item = short_text(part, 180)
        if item:
            lines.append(item)
        if len(lines) >= limit:
            break
    return lines


def suffix_from_url(url: str, content_type: str) -> str:
    path = urlparse(url).path.lower()
    for suffix in [".pdf", ".docx", ".json", ".csv", ".xml", ".html", ".htm", ".txt", ".md"]:
        if path.endswith(suffix):
            return suffix
    if "pdf" in content_type:
        return ".pdf"
    if "wordprocessingml" in content_type or "msword" in content_type:
        return ".docx"
    if "json" in content_type:
        return ".json"
    if "csv" in content_type:
        return ".csv"
    if "html" in content_type:
        return ".html"
    if "xml" in content_type:
        return ".xml"
    return ".bin"


def decode_text(data: bytes) -> str:
    for encoding in ["utf-8", "utf-8-sig", "gb18030", "gbk", "latin-1"]:
        try:
            return data.decode(encoding)
        except Exception:
            continue
    return data.decode("utf-8", errors="ignore")


def extract_pdf_text(path: str, max_chars: int) -> str:
    text_parts = []
    try:
        from pypdf import PdfReader

        reader = PdfReader(path)
        for page in reader.pages[:8]:
            page_text = page.extract_text() or ""
            if page_text.strip():
                text_parts.append(page_text)
            if len("\n".join(text_parts)) >= max_chars:
                break
    except Exception:
        try:
            import fitz

            with fitz.open(path) as doc:
                for page in doc[:8]:
                    page_text = page.get_text("text") or ""
                    if page_text.strip():
                        text_parts.append(page_text)
                    if len("\n".join(text_parts)) >= max_chars:
                        break
        except Exception:
            return ""
    return "\n".join(text_parts)[:max_chars]


def extract_docx_text(path: str, max_chars: int) -> str:
    try:
        from docx import Document

        doc = Document(path)
        parts = []
        for para in doc.paragraphs:
            text = (para.text or "").strip()
            if text:
                parts.append(text)
            if len("\n".join(parts)) >= max_chars:
                break
        return "\n".join(parts)[:max_chars]
    except Exception:
        return ""


def html_to_text(text: str) -> str:
    cleaned = re.sub(r"<script[\s\S]*?</script>", " ", text, flags=re.I)
    cleaned = re.sub(r"<style[\s\S]*?</style>", " ", cleaned, flags=re.I)
    cleaned = re.sub(r"<[^>]+>", " ", cleaned)
    cleaned = unescape(cleaned)
    return re.sub(r"\s+", " ", cleaned).strip()


def parse_structured_text(text: str, kind: str):
    if kind == "json":
        try:
            value = json.loads(text)
            pretty = json.dumps(value, ensure_ascii=False, indent=2)
            return pretty
        except Exception:
            return text
    if kind == "html":
        return html_to_text(text)
    return text


def summarize(text: str, kind: str, max_chars: int):
    excerpt = re.sub(r"\s+", " ", text or "").strip()[:max_chars]
    lines = split_lines(text, 5)
    if kind == "pdf":
        summary = "PDF 文档正文摘录"
    elif kind == "docx":
        summary = "DOCX 文档正文摘录"
    elif kind == "json":
        summary = "JSON 内容摘录"
    elif kind == "csv":
        summary = "CSV 内容摘录"
    elif kind == "html":
        summary = "网页文本摘录"
    else:
        summary = "文本内容摘录"
    return {
        "ok": True,
        "kind": kind,
        "summary": summary,
        "excerpt": excerpt,
        "lines": lines,
    }


def fetch_bytes(url: str, timeout_seconds: int):
    req = urllib.request.Request(url, headers={"User-Agent": "dris-deepresearch/0.1"})
    with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
        content_type = (resp.headers.get("Content-Type") or "").split(";")[0].strip().lower()
        data = resp.read(MAX_DOWNLOAD_BYTES)
    return data, content_type


def main():
    if len(sys.argv) < 5:
        print(json.dumps({"ok": False, "message": "usage: url content_type timeout_seconds max_chars"}, ensure_ascii=False))
        sys.exit(1)

    url = sys.argv[1]
    content_type_hint = (sys.argv[2] or "").strip().lower()
    timeout_seconds = int(sys.argv[3])
    max_chars = int(sys.argv[4])

    tmp_path = None
    try:
        data, fetched_content_type = fetch_bytes(url, timeout_seconds)
        content_type = fetched_content_type or content_type_hint
        suffix = suffix_from_url(url, content_type)

        if suffix in [".json", ".csv", ".xml", ".html", ".htm", ".txt", ".md"]:
            kind = suffix.lstrip(".")
            if kind == "htm":
                kind = "html"
            text = parse_structured_text(decode_text(data), kind)
            print(json.dumps(summarize(text, kind, max_chars), ensure_ascii=False))
            return

        fd, tmp_path = tempfile.mkstemp(suffix=suffix)
        with os.fdopen(fd, "wb") as f:
            f.write(data)

        if suffix == ".pdf":
            text = extract_pdf_text(tmp_path, max_chars)
            if text.strip():
                print(json.dumps(summarize(text, "pdf", max_chars), ensure_ascii=False))
                return
        if suffix == ".docx":
            text = extract_docx_text(tmp_path, max_chars)
            if text.strip():
                print(json.dumps(summarize(text, "docx", max_chars), ensure_ascii=False))
                return

        print(json.dumps({
            "ok": True,
            "kind": "binary",
            "summary": "二进制文件，当前未抽取出可用正文",
            "excerpt": "",
            "lines": []
        }, ensure_ascii=False))
    except Exception as exc:
        print(json.dumps({
            "ok": False,
            "message": str(exc)
        }, ensure_ascii=False))
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except Exception:
                pass


if __name__ == "__main__":
    main()
