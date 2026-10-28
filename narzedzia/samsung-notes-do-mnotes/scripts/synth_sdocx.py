#!/usr/bin/env python3
"""Syntetyczny .sdocx (i PDF zgodny z Notatkami) do samotestu skilla i fixture testu Kotlin.

Koduje dokładnie to podzbiór formatu, który czyta `sdocx.py`: tablicę napisów, kolejność stron, nagłówek strony
(kolor tła, wzór) i pociągnięcia w wersji skompresowanej (bit 0 maski) z przechyłem. Dzięki temu test przechodzi cały
tor wejścia bez prywatnych plików użytkownika. To nie jest pełny koder formatu Samsunga.
"""
import io
import struct
import zipfile
import zlib

PEN_INK = "com.samsung.android.sdk.pen.pen.preload.InkPen2"
PEN_BRUSH = "com.samsung.android.sdk.pen.pen.preload.BrushPen"
PEN_MARKER = "com.samsung.android.sdk.pen.pen.preload.Marker3"
STRINGS = {1: PEN_INK, 2: PEN_BRUSH, 3: PEN_MARKER}
PEN_ID = {PEN_INK: 1, PEN_BRUSH: 2, PEN_MARKER: 3}
PROPS = {PEN_INK: 0x425, PEN_BRUSH: 0x425, PEN_MARKER: 0x465}


def _mask(v):
    n = max(1, (v.bit_length() + 7) // 8)
    return bytes([n]) + v.to_bytes(n, "little")


def _s16(s):
    return struct.pack("<H", len(s)) + s.encode("utf-16le")


def _dq(v, scale):
    raw = min(int(round(abs(v) * scale)), 0x7FFF)
    return struct.pack("<H", raw | (0x8000 if v < 0 else 0))


def stroke_payload(pen, color, size, pts, uuid="s0"):
    """pts: lista (x, y, nacisk, t_ms) w jednostkach Samsunga; x, y najlepiej w wielokrotnościach 1/32."""
    n = len(pts)
    props, fmask = PROPS[pen], (1 << 2) | (1 << 3) | (1 << 7)
    u = uuid.encode("ascii")
    base = (struct.pack("<h", 0) + struct.pack("<I", 0) + _mask(0) + _mask(0) + struct.pack("<I", 1)
            + struct.pack("<H", len(u)) + u + struct.pack("<q", 0) + struct.pack("<4d", 0, 0, 0, 0))
    base = struct.pack("<I", 4 + len(base)) + base

    body = bytearray()
    x0, y0, p0, t0 = pts[0]
    body += struct.pack("<dd", x0, y0)
    px, py, pp, pt = x0, y0, p0, t0
    for x, y, _p, _t in pts[1:]:
        body += _dq(x - px, 32.0) + _dq(y - py, 32.0)
        px, py = x, y
    body += struct.pack("<f", p0)
    for _x, _y, p, _t in pts[1:]:
        body += _dq(p - pp, 4096.0)
        pp = p
    body += struct.pack("<i", t0)
    for _x, _y, _p, t in pts[1:]:
        body += struct.pack("<H", t - pt)
        pt = t
    for _ in range(2):  # przechył, bit 2 maski
        body += struct.pack("<f", 0.0) + b"\x00\x00" * (n - 1)
    body += b"\x00\x00"

    # ramka pociągnięcia: u32 rozmiar, i16 typ, u32 długość części stałej (do końca tablic), maski, u16 n, tablice
    fixed_len = 4 + 2 + 4 + len(_mask(props)) + len(_mask(fmask)) + 2 + len(body)
    extra = struct.pack("<I", color) + struct.pack("<f", size) + struct.pack("<i", PEN_ID[pen])
    frame = (struct.pack("<I", fixed_len + len(extra)) + struct.pack("<h", 1) + struct.pack("<I", fixed_len)
             + _mask(props) + _mask(fmask) + struct.pack("<H", n) + bytes(body) + extra)
    return base + frame


def _object(t, payload, kids=()):
    out = struct.pack("<BHI", t, len(kids), len(payload) + 32) + payload + b"\x00" * 32
    return out + b"".join(kids)


def page_bytes(uuid, strokes, bg=None, template=None, width=1600, height=2262):
    """strokes: lista słowników {'pen','color','size','pts'}. bg: ARGB papieru, template: numer wzoru Samsunga."""
    fm = 0
    flex = b""
    if bg is not None:
        fm |= 32
        flex += struct.pack("<I", bg)
    if template is not None:
        fm |= 512
        flex += struct.pack("<i", template)
    head_fixed = (_mask(0) + _mask(fm) + struct.pack("<IIIII", 0, width, height, 0, 0) + _s16(uuid)
                  + struct.pack("<q", 0) + struct.pack("<II", 1, 1))
    flex_off = 8 + len(head_fixed)
    layer_off = flex_off + len(flex)
    head = struct.pack("<II", layer_off, flex_off) + head_fixed
    objs = b"".join(_object(1, stroke_payload(s["pen"], s["color"], s["size"], s["pts"], "s%d" % i))
                    for i, s in enumerate(strokes))
    masks = _mask(0) + _mask(0)
    layer_head = struct.pack("<II", 8 + len(masks), 0) + masks
    layer = layer_head + struct.pack("<I", len(strokes)) + objs + b"\x00" * 32
    return head + flex + struct.pack("<HH", 1, 0) + layer


def note_note():
    entries = b"".join(struct.pack("<I", i) + _s16(s) for i, s in STRINGS.items())
    size = 2 + len(entries)
    return struct.pack("<IH", size, len(STRINGS)) + entries


def build_sdocx(pages):
    """pages: lista {'uuid','strokes','bg','template'}. Zwraca bajty ZIP-a .sdocx."""
    order = b"\x00" * 32 + struct.pack("<H", len(pages)) + b"".join(_s16(p["uuid"]) + b"\x00" * 32 for p in pages)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("note.note", note_note())
        z.writestr("pageIdInfo.dat", order)
        for p in pages:
            z.writestr(p["uuid"] + ".page", page_bytes(p["uuid"], p["strokes"], p.get("bg"), p.get("template")))
        z.writestr("end_tag.bin", b"\x00")
    return buf.getvalue()


def wrap_in_pdf(sdocx_bytes):
    """Minimalny „PDF zgodny z Notatkami”: osadzony .sdocx w strumieniu Flate (tak jak w PieceInfo/…/Bin0)."""
    data = zlib.compress(sdocx_bytes)
    return (b"%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n2 0 obj\n<< /Filter /FlateDecode /Length "
            + str(len(data)).encode() + b" >>\nstream\n" + data + b"\nendstream\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")


def sample_pages():
    """Dwie strony: z kratką i jasnym papierem (pióro, pędzel, zakreślacz) oraz gładka ciemna z jednym piórem."""
    def line(x, y, n, dx, dy, p, dt):
        return [(x + i * dx, y + i * dy, p(i), 1000 + i * dt) for i in range(n)]

    slow = line(100.0, 200.0, 30, 2.0, 0.5, lambda i: 0.5, 10)       # prędkość ok. 0,2 j./ms -> pełny rozmiar
    fast = line(100.0, 400.0, 30, 12.0, 0.0, lambda i: 0.5, 3)       # ok. 4 j./ms -> ok. 0,57 rozmiaru
    brush = line(100.0, 600.0, 30, 6.0, 1.0, lambda i: min(0.9, 0.1 + 0.03 * i), 8)
    marker = line(100.0, 800.0, 20, 20.0, 0.0, lambda i: 0.5, 8)
    return [
        {"uuid": "aaaaaaaa-0000-4000-8000-000000000001", "bg": 0xFFE6E6E6, "template": 5,
         "strokes": [{"pen": PEN_INK, "color": 0xFF000000, "size": 16.77, "pts": slow},
                     {"pen": PEN_INK, "color": 0xFF1565C0, "size": 16.77, "pts": fast},
                     {"pen": PEN_BRUSH, "color": 0xFFC62828, "size": 19.88, "pts": brush},
                     {"pen": PEN_MARKER, "color": 0x73FFEB3B, "size": 31.0, "pts": marker}]},
        {"uuid": "aaaaaaaa-0000-4000-8000-000000000002", "bg": 0xFFFCFCFC, "template": None,
         "strokes": [{"pen": PEN_INK, "color": 0xFF2E7D32, "size": 4.98, "pts": slow}]},
    ]


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "probka.sdocx"
    with open(out, "wb") as f:
        f.write(build_sdocx(sample_pages()))
    print("Zapisano:", out)
