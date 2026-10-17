#!/usr/bin/env python3
"""Zapis i odczyt zeszytu mNotes (.mnotes) z pociągnięciami (kreskami).

Format jest ten sam, który czyta aplikacja (`core/codec/PageCodec.kt`): strona MNP1 z rekordami `tag u8 | długość
varint | treść`; kreska to tag 1. Wszystkie liczby wielobajtowe są big-endian, współrzędne w 1/100 pt jako przyrosty
(zigzag). Moduł nie zależy od niczego poza biblioteką standardową.
"""
import json
import os
import struct
import time
import uuid
import zipfile
import zlib

Q = 100  # 1/100 pt, jak w aplikacji

# Kody piór (PenKind.code): nie zmieniać, to część formatu.
BALLPOINT, FOUNTAIN, BRUSH, HIGHLIGHTER, PENCIL = 0, 1, 2, 3, 4

TAG_STROKE = 1
FLAG_DEFLATE = 1
COVER = 0xFF3E7CB1

TEMPLATE_BLANK, TEMPLATE_LINED, TEMPLATE_GRID, TEMPLATE_DOTS = 0, 1, 2, 3


def signed32(v):
    """Kolory ARGB w plikach mNotes to liczby ze znakiem (jak Int w Kotlinie)."""
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v


def unsigned32(v):
    return v & 0xFFFFFFFF


def new_id():
    return uuid.uuid4().hex[:12]


def varint(n):
    out = bytearray()
    while n > 0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)
    return bytes(out)


def zigzag(v):
    return varint(((v << 1) ^ (v >> 31)) & 0xFFFFFFFF)


def _round_half_up(v):
    """Tak samo jak `roundToInt()` w Kotlinie (połówki w górę), a nie banker's rounding Pythona."""
    import math
    return int(math.floor(v + 0.5))


def encode_stroke(pen, color, width, pts):
    """Rekord kreski. pts: lista (x, y, p) w pt, p w 0..1; color: ARGB (liczba bez znaku albo ze znakiem)."""
    body = bytearray([pen])
    body += struct.pack(">i", signed32(color))
    body += varint(0)  # flagi: nie zablokowana, linia ciągła, bez prostowania
    body += varint(max(1, _round_half_up(width * Q)))
    body += varint(len(pts))
    px = py = 0
    for x, y, p in pts:
        ix, iy = _round_half_up(x * Q), _round_half_up(y * Q)
        body += zigzag(ix - px) + zigzag(iy - py)
        body.append(max(0, min(255, _round_half_up(p * 255.0))))
        px, py = ix, iy
    return bytes(body)


def encode_page(strokes):
    """Strona MNP1. strokes: lista {'pen','color','width','pts'} w kolejności warstw (pierwsza na spodzie)."""
    raw = bytearray(varint(len(strokes)))
    for s in strokes:
        body = encode_stroke(s["pen"], s["color"], s["width"], s["pts"])
        raw += bytes([TAG_STROKE]) + varint(len(body)) + body
    deflate = len(raw) > 64
    payload = zlib.compress(bytes(raw), 6) if deflate else bytes(raw)
    crc = zlib.crc32(payload) & 0xFFFFFFFF
    return b"MNP1" + bytes([1, FLAG_DEFLATE if deflate else 0]) + struct.pack(">I", crc) + varint(len(raw)) + payload


class _Reader:
    def __init__(self, b, p=0):
        self.b, self.p = b, p

    def u8(self):
        v = self.b[self.p]
        self.p += 1
        return v

    def i32(self):
        v = struct.unpack_from(">i", self.b, self.p)[0]
        self.p += 4
        return v

    def varint(self):
        v = shift = 0
        while True:
            b = self.u8()
            v |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                return v

    def zigzag(self):
        v = self.varint()
        return (v >> 1) ^ -(v & 1)


def decode_page(data):
    """Odczyt stron zapisanych przez encode_page (samokontrola i podgląd). Zwraca listę kresek (same kreski)."""
    if data[:4] != b"MNP1":
        raise ValueError("zły nagłówek strony")
    flags = data[5]
    crc = struct.unpack(">I", data[6:10])[0]
    r = _Reader(data, 10)
    raw_len = r.varint()
    payload = data[r.p:]
    if zlib.crc32(payload) & 0xFFFFFFFF != crc:
        raise ValueError("zła suma kontrolna strony")
    raw = zlib.decompress(payload) if flags & FLAG_DEFLATE else payload
    if len(raw) != raw_len:
        raise ValueError("zła długość strony")
    r = _Reader(raw)
    out = []
    for _ in range(r.varint()):
        tag = r.u8()
        ln = r.varint()
        end = r.p + ln
        if tag == TAG_STROKE:
            pen = r.u8()
            color = r.i32()
            flg = r.varint()
            width = r.varint() / Q
            n = r.varint()
            x = y = 0
            pts = []
            for _ in range(n):
                x += r.zigzag()
                y += r.zigzag()
                pts.append((x / Q, y / Q, r.u8() / 255.0))
            out.append({"pen": pen, "color": color, "width": width, "pts": pts, "flags": flg})
        r.p = end
    return out


def page_spec(w, h, paper, template=TEMPLATE_BLANK, line=None, spacing=0.0, line_width=0.6, opts=0):
    """Specyfikacja strony (klucze JSON jak w PageSpec.toJson). opts=0: bez pionowej linii marginesu."""
    if line is None:
        line = 0xFFD5DCE6
    return {"w": round(w, 2), "h": round(h, 2), "p": signed32(paper), "t": template, "l": signed32(line),
            "s": round(spacing, 2), "lw": round(line_width, 2), "o": opts}


def write_mnotes(path, title, pages):
    """pages: lista {'spec': dict, 'strokes': [...]}. Zapisuje ZIP .mnotes i zwraca meta."""
    now = int(time.time() * 1000)
    metas = []
    files = {}
    for p in pages:
        pid = new_id()
        metas.append({"id": pid, "s": p["spec"]})
        files["p/%s.mnp" % pid] = encode_page(p["strokes"])
    meta = {"v": 1, "id": new_id(), "title": title, "cr": now, "mod": now, "op": now, "cov": signed32(COVER),
            "cs": 0, "fav": False, "tr": 0, "lock": False, "last": 0, "lay": 0, "tags": [], "pages": metas, "bm": []}
    tmp = path + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("meta.json", json.dumps(meta, ensure_ascii=False))
        for name, data in files.items():
            z.writestr(name, data)
    os.replace(tmp, path)
    return meta


def read_mnotes(path):
    """Odczyt zeszytu z write_mnotes: (meta, [ {'spec', 'strokes'} ])."""
    with zipfile.ZipFile(path) as z:
        meta = json.loads(z.read("meta.json"))
        pages = [{"spec": pm["s"], "strokes": decode_page(z.read("p/%s.mnp" % pm["id"]))} for pm in meta["pages"]]
    return meta, pages
