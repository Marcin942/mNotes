#!/usr/bin/env python3
"""Buduje zeszyt .mnotes i/lub PDF z planu (plan.json z analyze.py).

Użycie:
  python3 build.py plan.json [--format mnotes|pdf|both] [--out-dir KATALOG] [--name NAZWA]
                             [--top-inset PT] [--gap PT] [--dpi 300] [--no-originals]

Układ: najpierw oryginalne strony (przeskalowane do A5, wektorowo), potem po jednej stronie A5 w kratkę na
zadanie albo podpunkt. Na stronie podpunktu są dwa osobne obrazki: treść zadania przed podpunktami i treść podpunktu.
"""
import argparse
import os
import shutil
import sys
import tempfile
import json
import io

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zm_common as zc  # noqa: E402
import pdfout  # noqa: E402


def load_plan(path):
    with open(path, encoding="utf-8") as f:
        plan = json.load(f)
    if plan.get("version") != 1 or "tasks" not in plan or "x_range" not in plan:
        zc.die("plan.json ma nieznany format (brak version=1, tasks albo x_range)")
    if not os.path.exists(plan["pdf"]):
        zc.die("nie ma pliku PDF wskazanego w planie: " + plan["pdf"])
    return plan


class Renderer:
    """Renderuje strony PDF raz (cache) i wycina z nich pasy."""

    def __init__(self, pdf, dpi):
        self.pdf = pdf
        self.dpi = dpi
        self.cache = {}

    def page(self, n):
        if n not in self.cache:
            self.cache[n] = zc.render_page(self.pdf, n, self.dpi)
        return self.cache[n]

    def crop(self, segs, x_range):
        from PIL import Image
        scale = self.dpi / 72.0
        parts = []
        for s in segs:
            im = self.page(s["page"])
            xl = max(0, int(round(x_range[0] * scale)))
            xr = min(im.width, int(round(x_range[1] * scale)))
            y0 = max(0, int(round(s["y0"] * scale)))
            y1 = min(im.height, int(round(s["y1"] * scale)))
            if y1 <= y0 or xr <= xl:
                zc.die("pusty wycinek (strona %s, y %s–%s): popraw plan.json" % (s["page"], s["y0"], s["y1"]))
            parts.append(im.crop((xl, y0, xr, y1)))
        if not parts:
            return None
        if len(parts) == 1:
            return parts[0]
        w = max(p.width for p in parts)
        h = sum(p.height for p in parts)
        out = Image.new("RGB", (w, h), (255, 255, 255))
        y = 0
        for p in parts:
            out.paste(p, (0, y))
            y += p.height
        return out


def maybe_gray(im):
    """Obrazek prawie bez koloru zapisujemy jako skalę szarości (mniejszy plik, ta sama treść)."""
    from PIL import ImageChops
    r, g, b = im.split()
    d = max(ImageChops.difference(r, g).getextrema()[1], ImageChops.difference(g, b).getextrema()[1])
    return im.convert("L") if d <= 6 else im


def png_bytes(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def task_pages(plan, rend, args):
    """Zwraca listę stron: {'name': opis, 'images': [(PIL.Image, cx, cy, w, h)]} oraz ostrzeżenia."""
    xr = plan["x_range"]
    width_pt = zc.A5_W
    pages = []
    warns = []

    def layout(name, ims):
        y = args.top_inset
        out = []
        for im in ims:
            h = width_pt * im.height / im.width
            out.append((im, width_pt / 2.0, y + h / 2.0, width_pt, h))
            y += h + args.gap
        total = y - args.gap
        if total > zc.A5_H:
            warns.append("strona „%s”: obrazki mają razem %.0f pt, a strona A5 tylko %.0f pt (wystają)" % (name, total, zc.A5_H))
        pages.append({"name": name, "images": out})

    for t in plan["tasks"]:
        intro = rend.crop(t["intro"], xr) if t.get("intro") else None
        if intro is not None:
            intro = maybe_gray(intro)
        subs = t.get("subs") or []
        if not subs:
            if intro is None:
                warns.append("zadanie %s nie ma żadnego wycinka — pominięte" % t["label"])
                continue
            layout(t["label"], [intro])
            continue
        for s in subs:
            sub = rend.crop(s["segs"], xr) if s.get("segs") else None
            if sub is not None:
                sub = maybe_gray(sub)
            ims = [i for i in (intro, sub) if i is not None]
            if not ims:
                warns.append("podpunkt %s %s nie ma wycinka — pominięty" % (t["label"], s["label"]))
                continue
            layout("%s %s" % (t["label"], s["label"]), ims)
    return pages, warns


def build_mnotes(plan, tpages, sizes, args, out_path):
    pages = []
    assets = {}
    if not args.no_originals:
        pdf_asset = zc.new_id() + ".pdf"
        with open(plan["pdf"], "rb") as f:
            assets[pdf_asset] = f.read()
        for i, (w, h) in enumerate(sizes):
            tw, th, _ = pdfout.target_size(w, h)
            pages.append({"spec": zc.pdf_background_spec(round(tw, 2), round(th, 2), pdf_asset, i), "images": []})
    for tp in tpages:
        imgs = []
        for im, cx, cy, w, h in tp["images"]:
            name = zc.new_id() + ".png"
            assets[name] = png_bytes(im)
            imgs.append((name, cx, cy, w, h))
        pages.append({"spec": zc.grid_spec(), "images": imgs})
    zc.write_mnotes(out_path, args.name, pages, assets)
    return len(pages)


def verify_mnotes(path, expected_pages):
    import zipfile
    with zipfile.ZipFile(path) as z:
        meta = json.loads(z.read("meta.json"))
        names = set(z.namelist())
        assert len(meta["pages"]) == expected_pages, "liczba stron w meta.json"
        for p in meta["pages"]:
            data = z.read("p/%s.mnp" % p["id"])
            for asset, cx, cy, w, h in zc.decode_page_images(data):
                assert "assets/" + asset in names, "brak zasobu " + asset
            if p["s"].get("pa"):
                assert "assets/" + p["s"]["pa"] in names, "brak tła PDF"
    return meta


def build_pdf(plan, tpages, sizes, args, out_path):
    with tempfile.TemporaryDirectory() as d:
        tasks_pdf = os.path.join(d, "zadania.pdf")
        pdfout.write_task_pdf(tasks_pdf, tpages)
        if args.no_originals:
            shutil.copyfile(tasks_pdf, out_path)
            return "bez oryginałów"
        scaled = os.path.join(d, "oryginaly.pdf")
        be = pdfout.scale_pdf(plan["pdf"], scaled, sizes)
        if be is None:
            return None
        pdfout.merge_pdfs([scaled, tasks_pdf], out_path)
        return be


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("plan")
    ap.add_argument("--format", choices=["mnotes", "pdf", "both"], default="mnotes")
    ap.add_argument("--out-dir", default=None)
    ap.add_argument("--name", default=None, help="tytuł zeszytu i nazwa pliku (domyślnie z planu)")
    ap.add_argument("--top-inset", type=float, default=0.0, help="odstęp obrazków od góry strony w pt (domyślnie 0)")
    ap.add_argument("--gap", type=float, default=0.0, help="odstęp między obrazkami na stronie w pt (domyślnie 0)")
    ap.add_argument("--dpi", type=int, default=300)
    ap.add_argument("--no-originals", action="store_true", help="bez oryginalnych stron na początku")
    args = ap.parse_args()

    plan = load_plan(args.plan)
    args.name = args.name or plan.get("title") or os.path.splitext(os.path.basename(plan["pdf"]))[0]
    out_dir = args.out_dir or zc.default_out_dir()
    os.makedirs(out_dir, exist_ok=True)
    safe = "".join(c if c.isalnum() or c in " -_." else "_" for c in args.name).strip() or "zeszyt"

    sizes = [tuple(s) for s in plan.get("page_sizes") or zc.read_page_sizes(plan["pdf"])]
    rend = Renderer(plan["pdf"], args.dpi)
    tpages, warns = task_pages(plan, rend, args)
    if not tpages:
        zc.die("plan nie zawiera żadnego zadania do wstawienia")

    produced = []
    if args.format in ("mnotes", "both"):
        path = os.path.join(out_dir, safe + ".mnotes")
        n = build_mnotes(plan, tpages, sizes, args, path)
        verify_mnotes(path, n)
        produced.append(path)
        zc.say("OK .mnotes: %s  (%d stron: %d oryginalnych + %d zadań)" % (path, n, 0 if args.no_originals else len(sizes), len(tpages)))
    if args.format in ("pdf", "both"):
        path = os.path.join(out_dir, safe + ".pdf")
        be = build_pdf(plan, tpages, sizes, args, path)
        if be is None:
            zc.say("UWAGA: nie ma narzędzia do skalowania PDF (pypdf, PyMuPDF, ghostscript) — PDF nie powstał. "
                   "Daj użytkownikowi .mnotes.")
        else:
            produced.append(path)
            zc.say("OK .pdf: %s  (%d stron zadań; skalowanie: %s)" % (path, len(tpages), be))
    for w in warns:
        zc.say("OSTRZEŻENIE: " + w)
    zc.say("Strony zadań:")
    for i, tp in enumerate(tpages, 1):
        zc.say("  %d. %s (%d obrazki)" % (i, tp["name"], len(tp["images"])))
    if not produced:
        sys.exit(1)


if __name__ == "__main__":
    main()
