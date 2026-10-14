#!/usr/bin/env python3
"""Krok 2: znajduje w PDF zadania i podpunkty i zapisuje plan wycinków (plan.json) oraz podglądy stron.

Użycie:  python3 analyze.py PLIK.pdf [--out KATALOG] [--title TYTUŁ] [--task-regex RE] [--sub-regex RE]

Tekst PDF służy tylko jako kotwica (gdzie zaczyna się zadanie). Granice wycinków wyznacza się po białych
przerwach na wyrenderowanej stronie, więc wzory i rysunki nie są ucinane.
"""
import argparse
import json
import os
import re
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zm_common as zc  # noqa: E402

ANALYSIS_DPI = 200
PAD_PT = 3.0           # zapas bieli wokół wycinka
MIN_SUBS = 2           # jedna „etykieta podpunktu” w zadaniu to nie lista podpunktów

WORDS = r"(?:zadanie|zad\.|problem|ćwiczenie|cwiczenie|ćw\.|exercise|ex\.|task|zagadnienie|question|pytanie)"
TASK_FAMILIES = [
    ("word", re.compile(r"^\*?\s*" + WORDS + r"\s*\d+(?:[.\-]\d+)*[.:)]?", re.I)),
    ("paren-dotted", re.compile(r"^\*?\s*\(\s*\d+(?:\.\d+)+\s*\)")),
    ("dotted", re.compile(r"^\*?\s*\d+(?:\.\d+)*[.)]\s")),
    ("paren-num", re.compile(r"^\*?\s*\(\s*\d{1,3}\s*\)")),
]
SUB_LABEL = re.compile(r"^\(?\s*([A-Za-z]{1,4}|\d{1,2})\s*\)")
ROMAN = re.compile(r"^[ivxlc]+$", re.I)
PAGE_NUMBER = re.compile(r"^(?:[-–—\s]*\d{1,4}[-–—\s]*|(?:strona|str\.|page|s\.)\s*\d+(?:\s*(?:/|z|of)\s*\d+)?)$", re.I)


def norm_key(text):
    return re.sub(r"\d+", "#", text.strip().lower())


class Page:
    def __init__(self, idx, w, h, lines):
        self.idx = idx
        self.w = w
        self.h = h
        self.lines = lines
        self.img = None
        self.mask = None
        self.rows = None
        self.sy = 1.0
        self.sx = 1.0
        self.top = 0.0       # górna granica treści (pt), pod nagłówkiem
        self.bottom = h      # dolna granica treści (pt), nad stopką


def classify_margin_lines(pages):
    """Numery stron i powtarzające się nagłówki/stopki są wyłączane z treści."""
    zone_seen = {}
    for p in pages:
        for ln in p.lines:
            if ln["y0"] < 0.10 * p.h or ln["y1"] > 0.85 * p.h:
                zone_seen.setdefault(norm_key(ln["text"]), set()).add(p.idx)
    for p in pages:
        for ln in p.lines:
            t = ln["text"].strip()
            in_top = ln["y1"] < 0.08 * p.h
            in_bottom = ln["y0"] > 0.85 * p.h
            ln["margin"] = False
            if not t:
                continue
            if (in_top or in_bottom) and PAGE_NUMBER.match(t):
                ln["margin"] = True
            elif (in_top or in_bottom) and len(pages) > 1 and len(zone_seen.get(norm_key(t), ())) >= 2:
                ln["margin"] = True
        tops = [ln["y1"] for ln in p.lines if ln["margin"] and ln["y1"] < 0.5 * p.h]
        bots = [ln["y0"] for ln in p.lines if ln["margin"] and ln["y0"] > 0.5 * p.h]
        p.top = max(tops) + 1.0 if tops else 0.0
        p.bottom = min(bots) - 1.0 if bots else p.h


def detect_tasks(pages, custom_regex):
    """Zwraca (nazwa_wzorca, lista startów). Start = {'page','y0','x0','label','text'}."""
    families = TASK_FAMILIES
    if custom_regex:
        families = [("custom", re.compile(custom_regex, re.I))]
    best = ("", [])
    for name, rx in families:
        hits = []
        for p in pages:
            for ln in p.lines:
                if ln["margin"] or not ln["text"]:
                    continue
                m = rx.match(ln["text"])
                if m:
                    hits.append({"page": p.idx, "y0": ln["y0"], "y1": ln["y1"], "x0": ln["x0"], "label": m.group(0).strip().lstrip("*").strip(),
                                 "text": ln["text"]})
        if hits:
            med = statistics.median(h["x0"] for h in hits)
            hits = [h for h in hits if abs(h["x0"] - med) <= 15]
        if len(hits) > len(best[1]):
            best = (name, hits)
    best[1].sort(key=lambda h: (h["page"], h["y0"]))
    return best


def roman_ok(token, upper):
    return bool(ROMAN.match(token)) and (token.isupper() == upper)


def detect_subs(pages, tasks, task_family, custom_sub):
    """Dla każdego zadania zwraca listę podpunktów (starty) albo []."""
    out = []
    for i, t in enumerate(tasks):
        nxt = tasks[i + 1] if i + 1 < len(tasks) else None
        cands = []
        for p in pages:
            if p.idx < t["page"] or (nxt and p.idx > nxt["page"]):
                continue
            for ln in p.lines:
                if ln["margin"] or not ln["text"]:
                    continue
                key = (p.idx, ln["y0"])
                if key <= (t["page"], t["y0"]) or (nxt and key >= (nxt["page"], nxt["y0"])):
                    continue
                if ln["x0"] < t["x0"] - 3:
                    continue
                if custom_sub:
                    m = re.match(custom_sub, ln["text"], re.I)
                    if m:
                        cands.append((p.idx, ln["y0"], ln["x0"], m.group(0).strip(), ln["text"], ln["y1"]))
                    continue
                m = SUB_LABEL.match(ln["text"])
                if m:
                    cands.append((p.idx, ln["y0"], ln["x0"], m.group(1), ln["text"], ln["y1"]))
        cands.sort()
        subs = []
        if custom_sub:
            subs = [{"page": c[0], "y0": c[1], "y1": c[5], "x0": c[2], "label": c[3], "text": c[4]} for c in cands]
        else:
            fam = None
            for c in cands:
                tok = c[3]
                if fam is None:
                    if tok in ("a", "A"):
                        fam = ("letters", tok.isupper())
                    elif tok in ("i", "I"):
                        fam = ("roman", tok.isupper())
                    elif tok == "1" and task_family in ("word", "paren-dotted"):
                        fam = ("digits", False)
                    else:
                        continue
                kind, upper = fam
                ok = ((kind == "letters" and len(tok) == 1 and tok.isalpha() and tok.isupper() == upper)
                      or (kind == "roman" and roman_ok(tok, upper))
                      or (kind == "digits" and tok.isdigit()))
                if ok:
                    shown = c[4].split(")")[0] + ")" if ")" in c[4][:8] else tok
                    subs.append({"page": c[0], "y0": c[1], "y1": c[5], "x0": c[2], "label": shown.strip(), "text": c[4]})
        if subs:
            med = statistics.median(s["x0"] for s in subs)
            subs = [s for s in subs if abs(s["x0"] - med) <= 14]
        out.append(subs if len(subs) >= MIN_SUBS else [])
    return out


# --- geometria na renderze ------------------------------------------------------------------

def prepare_images(pdf, pages):
    for p in pages:
        img = zc.render_page(pdf, p.idx + 1, ANALYSIS_DPI).convert("L")
        p.img = img
        p.mask = zc.ink_mask(img)
        p.rows = zc.row_has_ink(p.mask)
        p.sy = img.height / p.h
        p.sx = img.width / p.w


def white_runs(rows, lo, hi):
    """Białe przedziały [s, e] (indeksy wierszy) przecinające [lo, hi], rozszerzone do pełnej długości."""
    n = len(rows)
    runs = []
    y = max(lo, 0)
    while y <= min(hi, n - 1):
        if not rows[y]:
            s = y
            while s > 0 and not rows[s - 1]:
                s -= 1
            e = y
            while e < n - 1 and not rows[e + 1]:
                e += 1
            runs.append((s, e))
            y = e + 1
        else:
            y += 1
    return runs


def item_top(p, y0, y1, lh):
    """Góra elementu: linia z etykietą oraz jej odłamki (duże znaki całek, ułamki) w tym samym wierszu."""
    c = (y0 + y1) / 2.0
    top = y0
    for ln in p.lines:
        if ln["margin"] or ln["y0"] >= top:
            continue
        if abs((ln["y0"] + ln["y1"]) / 2.0 - c) <= 0.9 * lh and ln["y1"] > y0 - 0.5 * lh:
            top = ln["y0"]
    return top


def closed_rows(p, lh):
    """Wiersze z tuszem, w których wąskie białe szczeliny (< ok. 1,5 pt, np. między nadkreską a literą) liczą się jak tusz."""
    if getattr(p, "_crows", None) is not None:
        return p._crows
    rows = list(p.rows)
    gmin = max(1.0, 0.12 * lh) * p.sy
    n = len(rows)
    i = 0
    while i < n:
        if rows[i]:
            i += 1
            continue
        j = i
        while j < n and not rows[j]:
            j += 1
        if i > 0 and j < n and (j - i) < gmin:
            for k in range(i, j):
                rows[k] = True
        i = j
    p._crows = rows
    return rows


def pick_cut(p, top, lh):
    """Linia podziału (pt) nad elementem: środek białej przerwy, która leży bezpośrednio nad jego tuszem.

    Nadkreski (np. wektory Ā w zapisie matematycznym) wystają ponad ramkę linii, więc najpierw sięgamy
    do tuszu tuż nad górą linii (do 0,45 wysokości wiersza), o ile tworzy cienki pas, a nie sąsiednią linię.
    """
    rows = closed_rows(p, lh)
    n = len(rows)
    y = min(max(int(top * p.sy), 0), n - 1)
    lo = max(0, y - int(0.45 * lh * p.sy))
    ink = [r for r in range(lo, y + 1) if rows[r]]
    if ink and not rows[y]:
        r = min(ink)
        while r > 0 and rows[r - 1]:
            r -= 1
        if (y - r) / p.sy <= 0.7 * lh:
            y = r
    start = y
    while y > 0 and rows[y]:
        y -= 1
    if rows[y] or (start - y) / p.sy > 2.5 * lh:
        return max(0.0, top - 1.0), False
    s = y
    while s > 0 and not rows[s - 1]:
        s -= 1
    e = y
    while e < n - 1 and not rows[e + 1]:
        e += 1
    return (s + e + 1) / 2.0 / p.sy, True


def content_end(p, y_from, lh):
    """Dół treści ostatniego bloku na stronie: do pierwszej dużej białej przerwy albo do stopki."""
    gap = max(28.0, 3.2 * lh)
    y = int(y_from * p.sy)
    limit = min(int(p.bottom * p.sy), len(p.rows) - 1)
    last = y
    run = 0
    while y <= limit:
        if p.rows[y]:
            last = y
            run = 0
        else:
            run += 1
            if run / p.sy >= gap:
                break
        y += 1
    return (last + 1) / p.sy


def tight(p, y0, y1):
    """Zawęża [y0, y1] do tuszu plus zapas. Zwraca (y0, y1) albo None, gdy nie ma tuszu."""
    y0 = max(y0, p.top)
    y1 = min(y1, p.bottom)
    a, b = int(y0 * p.sy), int(y1 * p.sy)
    if b <= a:
        return None
    ys = [i for i in range(a, min(b, len(p.rows))) if p.rows[i]]
    if not ys:
        return None
    return max(y0, ys[0] / p.sy - PAD_PT), min(y1, (ys[-1] + 1) / p.sy + PAD_PT)


def x_extent(p, y0, y1):
    box = p.mask.crop((0, int(y0 * p.sy), p.mask.width, max(int(y1 * p.sy), int(y0 * p.sy) + 1))).getbbox()
    if not box:
        return None
    return box[0] / p.sx, box[2] / p.sx


# --- plan -----------------------------------------------------------------------------------

def build_plan(pages, tasks, subs_per_task, warnings):
    lhs = [ln["y1"] - ln["y0"] for p in pages for ln in p.lines if len(ln["text"]) >= 12 and not ln["margin"]]
    lh = statistics.median(lhs) if lhs else 11.0
    lh = min(max(lh, 8.0), 16.0)

    items = []   # kolejność w dokumencie: {'kind','task','sub','page','T','segs'}
    for ti, t in enumerate(tasks):
        items.append({"kind": "task", "ti": ti, "page": t["page"], "T": item_top(pages[t["page"]], t["y0"], t["y1"], lh), "segs": []})
        for si, s in enumerate(subs_per_task[ti]):
            items.append({"kind": "sub", "ti": ti, "si": si, "page": s["page"], "T": item_top(pages[s["page"]], s["y0"], s["y1"], lh), "segs": []})
    items.sort(key=lambda it: (it["page"], it["T"]))

    open_item = None
    fallback_cuts = 0
    for p in pages:
        here = [it for it in items if it["page"] == p.idx]
        if not here:
            if open_item is not None:
                end = content_end(p, p.top, lh)
                open_item["segs"].append((p.idx, p.top, end))
                if p.bottom - end > 5 * lh:
                    open_item = None
            continue
        cuts = []
        for it in here:
            c, ok = pick_cut(p, it["T"], lh)
            fallback_cuts += 0 if ok else 1
            cuts.append(c)
        if open_item is not None:
            open_item["segs"].append((p.idx, p.top, cuts[0]))
        elif p.top < cuts[0] and p.idx > 0:
            warnings.append("Pominięto treść nad pierwszym zadaniem na str. %d (traktuję jako nagłówek)." % (p.idx + 1))
        for i, it in enumerate(here):
            end = cuts[i + 1] if i + 1 < len(here) else content_end(p, cuts[i], lh)
            it["segs"].append((p.idx, cuts[i], end))
        last_end = content_end(p, cuts[-1], lh)
        open_item = here[-1] if p.bottom - last_end <= 5 * lh else None
    if fallback_cuts:
        warnings.append("W %d miejscach nie było białej przerwy przed początkiem zadania/podpunktu; cięcie jest przybliżone — sprawdź podgląd."
                        % fallback_cuts)

    # zawężenie do tuszu i wspólny zakres poziomy
    xs = []
    for it in items:
        new = []
        for (pi, y0, y1) in it["segs"]:
            tt = tight(pages[pi], y0, y1)
            if tt is None:
                continue
            ext = x_extent(pages[pi], tt[0], tt[1])
            if ext:
                xs.append(ext)
            new.append((pi, tt[0], tt[1]))
        it["segs"] = new
    if xs:
        xl = max(0.0, min(a for a, _ in xs) - 4.0)
        xr = max(b for _, b in xs) + 4.0
    else:
        xl, xr = 0.0, pages[0].w
    plan_tasks = []
    for ti, t in enumerate(tasks):
        entry = {"label": t["label"], "intro": [], "subs": [], "hint": t["text"][:70]}
        for it in items:
            if it["ti"] != ti:
                continue
            segs = [{"page": pi + 1, "y0": round(a, 1), "y1": round(b, 1)} for (pi, a, b) in it["segs"]]
            if it["kind"] == "task":
                entry["intro"] = segs
            else:
                s = subs_per_task[ti][it["si"]]
                entry["subs"].append({"label": s["label"], "segs": segs, "hint": s["text"][:70]})
        plan_tasks.append(entry)
    return plan_tasks, (round(xl, 1), round(xr, 1))


# --- podglądy -------------------------------------------------------------------------------

COLORS = [(30, 100, 220), (230, 120, 0), (20, 150, 80), (170, 40, 160), (200, 40, 40)]


def draw_previews(pdf, pages, plan, outdir):
    from PIL import Image, ImageDraw, ImageFont
    try:
        font = ImageFont.load_default(size=14)
    except TypeError:
        font = ImageFont.load_default()
    by_page = {}
    n = 0
    for ti, t in enumerate(plan["tasks"]):
        n += 1
        for s in t["intro"]:
            by_page.setdefault(s["page"], []).append((s["y0"], s["y1"], "Z%d" % (ti + 1), COLORS[0]))
        for si, sub in enumerate(t["subs"]):
            for s in sub["segs"]:
                by_page.setdefault(s["page"], []).append((s["y0"], s["y1"], "Z%d %s" % (ti + 1, sub["label"]), COLORS[1 + si % 4]))
    files = []
    for p in pages:
        scale = 1.6  # px na pt
        im = p.img.convert("RGB").resize((int(p.w * scale), int(p.h * scale)))
        ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        for y0, y1, text, col in by_page.get(p.idx + 1, []):
            d.rectangle([int(plan["x_range"][0] * scale), int(y0 * scale), int(plan["x_range"][1] * scale), int(y1 * scale)],
                        fill=col + (40,), outline=col + (255,), width=2)
            d.rectangle([int(plan["x_range"][0] * scale), int(y0 * scale), int(plan["x_range"][0] * scale) + 8 * len(text) + 8, int(y0 * scale) + 18],
                        fill=col + (255,))
            d.text((int(plan["x_range"][0] * scale) + 4, int(y0 * scale) + 1), text, fill=(255, 255, 255, 255), font=font)
        for y in range(0, int(p.h), 50):  # linijka w pt przy lewej krawędzi
            d.line([0, int(y * scale), 14, int(y * scale)], fill=(255, 0, 0, 255), width=1)
            d.text((16, int(y * scale) - 6), str(y), fill=(255, 0, 0, 255), font=font)
        im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
        f = os.path.join(outdir, "podglad-str%d.png" % (p.idx + 1))
        im.save(f)
        files.append(f)
    return files


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pdf")
    ap.add_argument("--out", default=None, help="katalog roboczy (domyślnie ./zm_praca)")
    ap.add_argument("--title", default=None, help="tytuł zeszytu (domyślnie nazwa pliku PDF)")
    ap.add_argument("--task-regex", default=None, help="własny wzorzec początku zadania (Python re, dopasowany od początku linii)")
    ap.add_argument("--sub-regex", default=None, help="własny wzorzec początku podpunktu")
    ap.add_argument("--no-subs", action="store_true", help="nie dziel zadań na podpunkty")
    a = ap.parse_args()

    pdf = os.path.abspath(a.pdf)
    if not os.path.isfile(pdf):
        zc.die("Nie ma pliku: " + pdf)
    outdir = os.path.abspath(a.out or "zm_praca")
    os.makedirs(outdir, exist_ok=True)
    title = a.title or os.path.splitext(os.path.basename(pdf))[0]

    text_pages = zc.read_pdf_text(pdf)
    if not text_pages:
        sizes = zc.read_page_sizes(pdf)
        if not sizes:
            zc.die("Nie umiem odczytać tego PDF-a (brak pdftotext, PyMuPDF, pypdf i pdfinfo).")
        text_pages = [{"w": w, "h": h, "lines": []} for w, h in sizes]
    pages = [Page(i, tp["w"], tp["h"], tp["lines"]) for i, tp in enumerate(text_pages)]
    warnings = []
    classify_margin_lines(pages)
    family, tasks = detect_tasks(pages, a.task_regex)
    if not any(p.lines for p in pages):
        warnings.append("PDF nie ma warstwy tekstowej (skan?). Trzeba ułożyć plan ręcznie — zob. references/plan-reczny.md.")
    prepare_images(pdf, pages)

    subs = [[] for _ in tasks] if a.no_subs else detect_subs(pages, tasks, family, a.sub_regex)
    plan_tasks, xr = build_plan(pages, tasks, subs, warnings) if tasks else ([], (0.0, pages[0].w))
    plan = {
        "version": 1,
        "title": title,
        "pdf": pdf,
        "page_sizes": [[round(p.w, 2), round(p.h, 2)] for p in pages],
        "task_pattern": family,
        "x_range": list(xr),
        "tasks": plan_tasks,
    }
    with open(os.path.join(outdir, "plan.json"), "w", encoding="utf-8") as f:
        json.dump(plan, f, ensure_ascii=False, indent=1)
    files = draw_previews(pdf, pages, plan, outdir)

    zc.say("Plik: %s  (%d str., %s pt)" % (os.path.basename(pdf), len(pages), " / ".join(sorted({"%gx%g" % (round(p.w), round(p.h)) for p in pages}))))
    zc.say("Wzorzec zadań: %s;  zadań: %d;  wspólny zakres poziomy: %g–%g pt" % (family or "brak", len(plan_tasks), xr[0], xr[1]))
    for i, t in enumerate(plan_tasks, 1):
        sub = " podpunkty: " + " ".join(s["label"] for s in t["subs"]) if t["subs"] else " bez podpunktów"
        pos = ", ".join("str.%d y=%g–%g" % (s["page"], s["y0"], s["y1"]) for s in t["intro"]) or "(pusty!)"
        zc.say("%2d. %s |%s | %s" % (i, t["label"], sub, pos))
        for s in t["subs"]:
            zc.say("      %s: %s" % (s["label"], ", ".join("str.%d y=%g–%g" % (g["page"], g["y0"], g["y1"]) for g in s["segs"]) or "(pusty!)"))
    for w in warnings:
        zc.say("UWAGA: " + w)
    zc.say("Plan: " + os.path.join(outdir, "plan.json"))
    zc.say("Podglądy: " + " ".join(files))


if __name__ == "__main__":
    main()
