#!/usr/bin/env python3
"""Podgląd zeszytu .mnotes jako PNG (do sprawdzenia wyniku konwersji bez uruchamiania aplikacji).

Użycie:
    python3 preview.py ZESZYT.mnotes KATALOG_WYJŚCIOWY [--scale 2.667]

Rysuje papier, wzór tła (siatka tak samo jak `TemplateGeometry`) i kreski: pióra o zmiennej grubości jako suma
kółek o promieniu `nacisk * szerokość / 2`, zakreślacz jako mnożenie z krytością 0,5. Wymaga Pillow i numpy.
To przybliżenie wyglądu w aplikacji (bez wygładzania i zbiegania końców), nie jej renderer.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mnotes_io as mio  # noqa: E402


def argb(v):
    v &= 0xFFFFFFFF
    return (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF


def draw_template(img, spec, s):
    t, sp = spec["t"], max(spec["s"], 3.0)
    if t == mio.TEMPLATE_BLANK:
        return
    d = ImageDraw.Draw(img)
    col = argb(spec["l"])
    lw = max(1, round(spec["lw"] * s))
    w, h = spec["w"], spec["h"]
    if t == mio.TEMPLATE_GRID:
        x = (w % sp) / 2
        while x <= w + 0.01:
            d.line([(x * s, 0), (x * s, h * s)], fill=col, width=lw)
            x += sp
        y = (h % sp) / 2
        while y <= h + 0.01:
            d.line([(0, y * s), (w * s, y * s)], fill=col, width=lw)
            y += sp
    elif t == mio.TEMPLATE_LINED:
        y = sp * 2
        while y <= h - sp * 0.5:
            d.line([(0, y * s), (w * s, y * s)], fill=col, width=lw)
            y += sp
    elif t == mio.TEMPLATE_DOTS:
        y = (h % sp) / 2
        while y <= h + 0.01:
            x = (w % sp) / 2
            while x <= w + 0.01:
                d.ellipse([x * s - lw, y * s - lw, x * s + lw, y * s + lw], fill=col)
                x += sp
            y += sp


def stroke_mask(size, st, s):
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    pts = st["pts"]
    half = st["width"] * s / 2.0
    const = st["pen"] in (mio.BALLPOINT, mio.HIGHLIGHTER)
    if len(pts) == 1:
        x, y, p = pts[0]
        r = half * (1.0 if const else max(p, 0.04))
        d.ellipse([x * s - r, y * s - r, x * s + r, y * s + r], fill=255)
        return mask
    for (x0, y0, p0), (x1, y1, p1) in zip(pts, pts[1:]):
        steps = max(1, int(math.hypot(x1 - x0, y1 - y0) * s / 0.7))
        for i in range(steps + 1):
            f = i / steps
            x, y, p = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, p0 + (p1 - p0) * f
            r = half * (1.0 if const else max(p, 0.04))
            d.ellipse([x * s - r, y * s - r, x * s + r, y * s + r], fill=255)
    return mask


def render_page(page, scale):
    spec = page["spec"]
    size = (int(round(spec["w"] * scale)), int(round(spec["h"] * scale)))
    img = Image.new("RGB", size, argb(spec["p"]))
    draw_template(img, spec, scale)
    arr = np.asarray(img).astype(np.float32)
    for st in page["strokes"]:
        m = np.asarray(stroke_mask(size, st, scale)).astype(np.float32)[..., None] / 255.0
        c = np.array(argb(st["color"]), dtype=np.float32)
        if st["pen"] == mio.HIGHLIGHTER:
            target = arr * (c / 255.0)
            arr = arr + (target - arr) * 0.5 * m
        else:
            arr = arr + (c - arr) * m
    return Image.fromarray(np.clip(arr + 0.5, 0, 255).astype(np.uint8))


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    scale = 2.667
    if "--scale" in args:
        i = args.index("--scale")
        scale = float(args[i + 1])
        del args[i:i + 2]
    if len(args) != 2:
        raise SystemExit(__doc__)
    src, outdir = args
    os.makedirs(outdir, exist_ok=True)
    _meta, pages = mio.read_mnotes(src)
    for i, pg in enumerate(pages, 1):
        path = os.path.join(outdir, "strona_%d.png" % i)
        render_page(pg, scale).save(path)
        print(path)


if __name__ == "__main__":
    main()
