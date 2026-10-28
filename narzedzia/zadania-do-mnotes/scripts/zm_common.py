#!/usr/bin/env python3
"""Wspólne narzędzia skilla „zadania-do-mnotes”.

Zawiera: wykrywanie narzędzi (poppler, PyMuPDF, ghostscript, pypdf), renderowanie stron PDF,
odczyt tekstu z pozycjami, zapis stron i zeszytu w formacie mNotes (.mnotes) oraz stałe papieru A5 w kratkę.
Skrypt wymaga tylko Pythona 3 i Pillow; reszta jest opcjonalna i wykrywana w locie.
"""
import html
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import uuid
import zipfile
import zlib

A5_W = 420.0
A5_H = 595.0
GRID_SPACING = 14.17
GRID_LINE_RGB = (213, 220, 230)  # Paper.autoLineColor(biały) = 0xFFD5DCE6
COVER = 0xFF3E7CB1
TEMPLATE_GRID = 2
OUT_FALLBACK = "/mnt/user-data/outputs"


def signed32(v):
    """Kolory ARGB w plikach mNotes to liczby ze znakiem (jak Int w Kotlinie)."""
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v


def which(name):
    return shutil.which(name)


def have_module(name):
    try:
        __import__(name)
        return True
    except Exception:
        return False


def import_fitz():
    for name in ("fitz", "pymupdf"):
        try:
            return __import__(name)
        except Exception:
            continue
    return None


def default_out_dir():
    return OUT_FALLBACK if os.path.isdir(OUT_FALLBACK) else os.getcwd()


def say(msg):
    print(msg, flush=True)


def die(msg, code=2):
    print("BŁĄD: " + msg, file=sys.stderr, flush=True)
    sys.exit(code)


# --- odczyt PDF -----------------------------------------------------------------------------

def _run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, **kw)


def _parse_bbox_layout(text):
    pages = []
    for pg in re.finditer(r'<page width="([\d.]+)" height="([\d.]+)">(.*?)</page>', text, re.S):
        lines = []
        for m in re.finditer(r'<line xMin="([\d.\-]+)" yMin="([\d.\-]+)" xMax="([\d.\-]+)" yMax="([\d.\-]+)">(.*?)</line>',
                             pg.group(3), re.S):
            words = [html.unescape(w) for w in re.findall(r'>([^<]*)</word>', m.group(5))]
            t = " ".join(w for w in words if w != "").strip()
            lines.append({"x0": float(m.group(1)), "y0": float(m.group(2)), "x1": float(m.group(3)),
                          "y1": float(m.group(4)), "text": t})
        pages.append({"w": float(pg.group(1)), "h": float(pg.group(2)), "lines": lines})
    return pages


def _fitz_pages(pdf):
    fitz = import_fitz()
    if fitz is None:
        return None
    doc = fitz.open(pdf)
    pages = []
    for page in doc:
        rect = page.rect
        lines = []
        for b in page.get_text("dict").get("blocks", []):
            for ln in b.get("lines", []):
                t = "".join(sp.get("text", "") for sp in ln.get("spans", [])).strip()
                x0, y0, x1, y1 = ln["bbox"]
                lines.append({"x0": x0, "y0": y0, "x1": x1, "y1": y1, "text": t})
        pages.append({"w": float(rect.width), "h": float(rect.height), "lines": lines})
    return pages


def read_pdf_text(pdf):
    """Zwraca listę stron: {'w','h' (pt, po obrocie), 'lines': [{x0,y0,x1,y1,text}]}; y rośnie w dół."""
    if which("pdftotext"):
        r = _run(["pdftotext", "-bbox-layout", pdf, "-"])
        if r.returncode == 0:
            pages = _parse_bbox_layout(r.stdout.decode("utf-8", "replace"))
            if pages:
                return pages
    try:
        pages = _fitz_pages(pdf)
        if pages:
            return pages
    except Exception:
        pass
    return None


def page_sizes_fallback(pdf):
    """Rozmiary stron, gdy nie ma odczytu tekstu. Zwraca listę (w, h) w pt albo None."""
    try:
        from pypdf import PdfReader
        out = []
        for p in PdfReader(pdf).pages:
            w, h = float(p.mediabox.width), float(p.mediabox.height)
            if (p.get("/Rotate") or 0) % 180 == 90:
                w, h = h, w
            out.append((w, h))
        return out
    except Exception:
        pass
    if which("pdfinfo"):
        r = _run(["pdfinfo", "-f", "1", "-l", "99999", pdf])
        out = [(float(a), float(b)) for a, b in re.findall(r"Page\s+\d+ size:\s+([\d.]+) x ([\d.]+) pts", r.stdout.decode("utf-8", "replace"))]
        if out:
            return out
    return None


def read_page_sizes(pdf, pages=None):
    pages = pages or read_pdf_text(pdf)
    if pages:
        return [(p["w"], p["h"]) for p in pages]
    return page_sizes_fallback(pdf)


def render_page(pdf, n, dpi):
    """Renderuje stronę n (od 1) jako obraz Pillow RGB. Próbuje: pdftoppm, PyMuPDF, ghostscript, mutool."""
    from PIL import Image
    with tempfile.TemporaryDirectory() as d:
        if which("pdftoppm"):
            r = _run(["pdftoppm", "-r", str(dpi), "-f", str(n), "-l", str(n), "-png", "-singlefile", pdf, os.path.join(d, "p")])
            f = os.path.join(d, "p.png")
            if r.returncode == 0 and os.path.exists(f):
                im = Image.open(f)
                im.load()
                return im.convert("RGB")
        fitz = import_fitz()
        if fitz is not None:
            try:
                pix = fitz.open(pdf)[n - 1].get_pixmap(dpi=dpi, alpha=False)
                return Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
            except Exception:
                pass
        if which("gs"):
            f = os.path.join(d, "g.png")
            r = _run(["gs", "-q", "-dNOPAUSE", "-dBATCH", "-sDEVICE=png16m", "-r%d" % dpi, "-dTextAlphaBits=4",
                      "-dGraphicsAlphaBits=4", "-dFirstPage=%d" % n, "-dLastPage=%d" % n, "-sOutputFile=" + f, pdf])
            if r.returncode == 0 and os.path.exists(f):
                im = Image.open(f)
                im.load()
                return im.convert("RGB")
        if which("mutool"):
            f = os.path.join(d, "m.png")
            r = _run(["mutool", "draw", "-r", str(dpi), "-o", f, pdf, str(n)])
            if r.returncode == 0 and os.path.exists(f):
                im = Image.open(f)
                im.load()
                return im.convert("RGB")
    die("Nie mogę wyrenderować strony %d (brak pdftoppm, PyMuPDF, ghostscript i mutool). Zainstaluj poppler-utils lub PyMuPDF." % n)


# --- analiza obrazu (tusz) ------------------------------------------------------------------

INK_LEVEL = 210  # piksel ciemniejszy niż to (0–255) jest „tuszem”


def ink_mask(gray):
    return gray.point(lambda v: 255 if v < INK_LEVEL else 0)


def row_has_ink(mask):
    """Lista bool: czy wiersz zawiera tusz. Używa numpy, a bez niego pętli po wierszach (wolniej, ten sam wynik)."""
    try:
        import numpy as np
        return [bool(x) for x in np.asarray(mask).any(axis=1)]
    except ImportError:
        w, h = mask.size
        return [mask.crop((0, y, w, y + 1)).getbbox() is not None for y in range(h)]


# --- format mNotes --------------------------------------------------------------------------

def new_id():
    return uuid.uuid4().hex[:12]


def varint(n):
    out = bytearray()
    while n > 0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)
    return bytes(out)


def encode_image_el(asset, cx, cy, w, h):
    """Element obrazu (tag 3): flagi=0 (nie zablokowany), środek, rozmiar, obrót 0, alfa 255, nazwa zasobu."""
    a = asset.encode("utf-8")
    return varint(0) + struct.pack(">fffff", cx, cy, w, h, 0.0) + bytes([255]) + varint(len(a)) + a


def encode_page(images):
    """Strona MNP1. images: lista (asset, cx, cy, w, h) w kolejności warstw (pierwszy na spodzie)."""
    raw = bytearray(varint(len(images)))
    for asset, cx, cy, w, h in images:
        body = encode_image_el(asset, cx, cy, w, h)
        raw += bytes([3]) + varint(len(body)) + body
    deflate = len(raw) > 64
    payload = zlib.compress(bytes(raw), 1) if deflate else bytes(raw)
    crc = zlib.crc32(payload) & 0xFFFFFFFF
    return b"MNP1" + bytes([1, 1 if deflate else 0]) + struct.pack(">I", crc) + varint(len(raw)) + payload


def decode_page_images(data):
    """Odczyt stron zapisanych przez encode_page (do samokontroli). Zwraca listę (asset, cx, cy, w, h)."""
    assert data[:4] == b"MNP1", "zły nagłówek"
    flags = data[5]
    crc = struct.unpack(">I", data[6:10])[0]
    pos = 10
    raw_len = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        raw_len |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            break
    payload = data[pos:]
    assert zlib.crc32(payload) & 0xFFFFFFFF == crc, "zła suma kontrolna"
    raw = zlib.decompress(payload) if flags & 1 else payload
    assert len(raw) == raw_len, "zła długość"

    def rd_varint(buf, p):
        v = 0
        s = 0
        while True:
            b = buf[p]
            p += 1
            v |= (b & 0x7F) << s
            s += 7
            if not b & 0x80:
                return v, p

    count, p = rd_varint(raw, 0)
    out = []
    for _ in range(count):
        tag = raw[p]
        ln, p = rd_varint(raw, p + 1)
        body = raw[p:p + ln]
        p += ln
        if tag != 3:
            continue
        _, q = rd_varint(body, 0)
        cx, cy, w, h, _rot = struct.unpack(">fffff", body[q:q + 20])
        q += 21
        n, q = rd_varint(body, q)
        out.append((body[q:q + n].decode("utf-8"), cx, cy, w, h))
    return out


def grid_spec(w=A5_W, h=A5_H):
    """Strona w kratkę, biały papier — to samo, co tworzy aplikacja (wzór GRID, rozstaw 14,17 pt)."""
    return {"w": w, "h": h, "p": signed32(0xFFFFFFFF), "t": TEMPLATE_GRID,
            "l": signed32(0xFF000000 | (GRID_LINE_RGB[0] << 16) | (GRID_LINE_RGB[1] << 8) | GRID_LINE_RGB[2]),
            "s": GRID_SPACING, "lw": 0.6, "o": 1}


def pdf_background_spec(w, h, asset, page_index):
    s = grid_spec(w, h)
    s.update({"t": 0, "s": 0.0})  # strona z tłem PDF: pusty wzór, jak po imporcie PDF w aplikacji
    s["pa"] = asset
    s["pn"] = page_index
    return s


def write_mnotes(path, title, pages, assets):
    """pages: lista {'spec': dict, 'images': [(asset,cx,cy,w,h)]}; assets: {nazwa: bajty}. Zapisuje ZIP .mnotes."""
    import time
    now = int(time.time() * 1000)
    metas = []
    pagefiles = {}
    for p in pages:
        pid = new_id()
        metas.append({"id": pid, "s": p["spec"]})
        pagefiles["p/%s.mnp" % pid] = encode_page(p["images"])
    meta = {"v": 1, "id": new_id(), "title": title, "cr": now, "mod": now, "op": now, "cov": signed32(COVER), "cs": 0,
            "fav": False, "tr": 0, "lock": False, "last": 0, "lay": 0, "tags": [], "pages": metas, "bm": []}
    tmp = path + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("meta.json", json.dumps(meta, ensure_ascii=False))
        for name, data in pagefiles.items():
            z.writestr(name, data)
        for name, data in assets.items():
            z.writestr("assets/" + name, data, compress_type=zipfile.ZIP_STORED)
    os.replace(tmp, path)
    return meta
