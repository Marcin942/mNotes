#!/usr/bin/env python3
"""Samsung Notes (.sdocx albo „PDF zgodny z Notatkami”) -> zeszyt mNotes (.mnotes).

Etap pierwszy: pociągnięcia (pióro, długopis, pędzel, zakreślacz) i tło strony (kolor papieru, kratka). Wszystko, co
daje się przenieść, trafia do mNotes jako natywne, edytowalne obiekty (kreski), nie jako obraz. Czego mNotes nie ma
albo czego nie umiemy jeszcze odczytać, zostaje najbliższym odpowiednikiem albo jest pominięte i wylądowało
w raporcie odstępstw (plik `.raport.txt` obok wyniku oraz wydruk na ekranie).

Użycie:
    python3 convert.py WEJŚCIE.(sdocx|pdf) [-o WYNIK.mnotes] [--title TYTUŁ] [--dark] [--paper RRGGBB]

Wymaga tylko Pythona 3 (biblioteka standardowa).
"""
import argparse
import colorsys
import math
import os
import re
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mnotes_io as mio  # noqa: E402
import sdocx  # noqa: E402

UNIT_PT = 0.375           # 1600 jednostek Samsunga = 600 pt
MIN_STEP_PT = 0.3         # minimalny odstęp zachowanych próbek (mNotes filtruje próbki co ok. 0,2 pt)
DP_PRESSURE_STEP = 0.06   # dodatkowo zachowaj próbkę, gdy nacisk zmienił się o tyle (pędzel)

# Skalibrowane na rastrze PDF zgodnego z Notatkami (patrz SKILL.md).
BRUSH_K = 1.2             # szerokość pędzla mNotes = rozmiar Samsunga * UNIT_PT * BRUSH_K
BRUSH_A, BRUSH_B = 0.09, 1.17   # szerokość/rozmiar = A + B * nacisk (pędzel Samsunga)
PRESSURE_MIN = 0.04       # PenSpec.factor przycina do 0,04; niżej kreska byłaby niewidoczna
# InkPen2 nie zmienia grubości z naciskiem, tylko z prędkością pisania: wolno = pełny rozmiar, szybko cieńsza
# kreska (zmierzone na 13 kreskach rozmiaru 16,77 i 4,98: współczynnik szerokość/rozmiar vs. prędkość).
# Pary (prędkość w jednostkach Samsunga na ms, współczynnik); poza zakresem wartość skrajna.
INK_SPEED_TABLE = [(0.0, 1.0), (0.6, 1.0), (1.1, 0.88), (1.75, 0.66), (2.5, 0.60), (6.5, 0.55)]
INK_SPEED_HALF_WINDOW = 2  # punktów po każdej stronie przy liczeniu prędkości (czas jest w pełnych ms)

# Siatka „średnia” (wzór 5) — zmierzona na stronie 1600x2262: rozstaw 102 jednostki, linie ok. #C8C8C8.
GRID_PITCH_PT = 38.25
GRID_OFFSET_X_PT = 0.10
GRID_OFFSET_Y_PT = 16.71
GRID_LINE = 0xFFC8C8C8
GRID_LINE_WIDTH_PT = 1.0
TEMPLATE_PITCH_FACTOR = {1: 2 / 3, 2: 1.0, 3: 4 / 3, 4: 2 / 3, 5: 1.0, 6: 4 / 3, 7: 2 / 3, 8: 1.0, 9: 4 / 3}
VERIFIED_TEMPLATES = {5}

# Tryb ciemny Samsung Notes to wygląd w aplikacji, nie dane pliku (.sdocx i oba PDF-y niosą notatkę jasną). Zmierzone
# na zrzucie ekranu: papier FCFCFC -> 030303, pismo 030303 -> FCFCFC, 123644 -> BBDFED, czyli odwrócenie jasności HLS
# z zachowaniem barwy i nasycenia; zakreślacz to zwykła alfa (0x73) nałożona na ciemny papier.
HIGHLIGHT_ALPHA = 0.5     # PenSpec HIGHLIGHTER.alpha w mNotes
DARK_SCREEN_FACTOR = 0.84 # PageRenderer.styleHighlighter: na ciemnym papierze alfa * 0,84, tryb „screen”

OBJECT_NAMES = {2: "pole tekstowe", 3: "obraz", 4: "kontener", 7: "kształt", 8: "linia", 10: "nagranie głosu",
                11: "wzór matematyczny", 12: "tabela (stara)", 13: "strona WWW", 14: "malowanie", 16: "wideo",
                17: "łącze", 18: "pociągnięcie pędzlem", 20: "wykres", 21: "matematyka", 22: "tabela",
                23: "blok kodu", 24: "załącznik"}
TEMPLATE_NAMES = {10: "lista zadań", 11: "papier Oxford", 12: "własny obraz", 13: "tygodniowy", 14: "miesięczny",
                  15: "rękopis", 16: "PDF"}


class Report:
    def __init__(self):
        self.lines = []
        self.once = set()

    def note(self, text):
        self.lines.append(text)

    def warn_once(self, key, text):
        if key not in self.once:
            self.once.add(key)
            self.lines.append(text)


# --- wejście -----------------------------------------------------------------------------------

def sdocx_from_pdf(path):
    """Wyjmuje osadzony .sdocx z PDF-a zgodnego z Notatkami (strumień Flate zaczynający się od `PK`)."""
    with open(path, "rb") as f:
        data = f.read()
    pos = 0
    while True:
        i = data.find(b"stream", pos)
        if i < 0:
            break
        if data[i - 3:i] == b"end":
            pos = i + 6
            continue
        j = i + 6
        if data[j:j + 2] == b"\r\n":
            j += 2
        elif data[j:j + 1] in (b"\n", b"\r"):
            j += 1
        k = data.find(b"endstream", j)
        if k < 0:
            break
        try:
            d = zlib.decompressobj().decompress(data[j:k])
        except zlib.error:
            d = b""
        if d[:4] == b"PK\x03\x04":
            try:
                with zipfile.ZipFile(__import__("io").BytesIO(d)) as z:
                    if "note.note" in z.namelist():
                        return d
            except zipfile.BadZipFile:
                pass
        pos = k + 9
    raise SystemExit("Ten PDF nie zawiera notatki Samsung Notes (to zwykły PDF z samym rastrem). Wyeksportuj notatkę "
                     "jako „PDF zgodny z Notatkami” albo jako plik .sdocx.")


def load_input(path, tmpdir):
    if path.lower().endswith(".pdf"):
        raw = sdocx_from_pdf(path)
        tmp = os.path.join(tmpdir, "osadzony.sdocx")
        with open(tmp, "wb") as f:
            f.write(raw)
        return sdocx.load_sdocx(tmp)
    return sdocx.load_sdocx(path)


# --- kolory -------------------------------------------------------------------------------------

def rgb(c):
    return (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF


def pack(a, r, g, b):
    return (a << 24) | (r << 16) | (g << 8) | b


def invert_lightness(r, g, b):
    """Ciemny tryb: odwraca jasność koloru z zachowaniem barwy (czarny -> biały, ciemny turkus -> jasny)."""
    h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
    r2, g2, b2 = colorsys.hls_to_rgb(h, 1.0 - l, s)
    return round(r2 * 255), round(g2 * 255), round(b2 * 255)


def highlighter_color(c0, paper):
    """Kolor zapisywany dla zakreślacza mNotes (multiply z alfą 0,5), by wynik na papierze `paper` był taki jak
    w Samsung Notes (tam: P + (c0 - P) * alfa, ale nigdy jaśniej niż papier)."""
    a = (c0 >> 24) / 255.0
    out = []
    for c, p in zip(rgb(c0), rgb(paper)):
        t = min(p, p + (c - p) * a)
        v = 255.0 * (t / p - 0.5) / 0.5 if p > 0 else 255.0
        out.append(int(round(max(0.0, min(255.0, v)))))
    return pack(0xFF, *out)


def invert_argb(c):
    r, g, b = invert_lightness(*rgb(c))
    return pack(c >> 24, r, g, b)


def highlighter_color_dark(c0, paper):
    """Kolor zakreślacza mNotes na ciemnym papierze. Samsung nakłada go zwykłą alfą (P + (C - P) * a), mNotes
    rozświetla „screen” z kryciem 0,5 * 0,84; odwracamy wzór screen, żeby wynik był ten sam. Gdy takie krycie nie
    wystarcza (kolor jaśniejszy, niż screen da z krycia 0,42), wartość jest przycinana, więc wynik jest nieco ciemniejszy."""
    a = (c0 >> 24) / 255.0
    am = HIGHLIGHT_ALPHA * DARK_SCREEN_FACTOR
    out = []
    for c, p in zip(rgb(c0), rgb(paper)):
        t = (p + (c - p) * a) / 255.0
        pp = p / 255.0
        v = (1.0 - (1.0 - t) / (1.0 - pp)) / am if pp < 1.0 else 1.0
        out.append(int(round(255.0 * max(0.0, min(1.0, v)))))
    return pack(0xFF, *out)


# --- pióra --------------------------------------------------------------------------------------

def pen_kind(st):
    name = (st.pen or "").lower()
    if "marker" in name or "highlight" in name or st.props & 0x40:
        return "marker", True
    if "brush" in name:
        return "brush", True
    if "inkpen" in name:
        return "ink", True
    return "other", False


def brush_factor(p):
    return BRUSH_A + BRUSH_B * p


def ink_speed_factor(v):
    tbl = INK_SPEED_TABLE
    if v <= tbl[0][0]:
        return tbl[0][1]
    for (v0, f0), (v1, f1) in zip(tbl, tbl[1:]):
        if v <= v1:
            return f0 + (f1 - f0) * (v - v0) / (v1 - v0)
    return tbl[-1][1]


def ink_factors(st):
    """Współczynnik grubości InkPen2 w każdym punkcie, z prędkości (jednostki Samsunga na ms), lekko wygładzony."""
    n = len(st.pts)
    raw = []
    for i in range(n):
        a, b = max(0, i - INK_SPEED_HALF_WINDOW), min(n - 1, i + INK_SPEED_HALF_WINDOW)
        dt = st.pts[b][3] - st.pts[a][3]
        d = math.hypot(st.pts[b][0] - st.pts[a][0], st.pts[b][1] - st.pts[a][1])
        raw.append(ink_speed_factor(d / dt) if dt > 0 else 1.0)
    out = []
    for i in range(n):
        w = raw[max(0, i - 1):i + 2]
        out.append(max(PRESSURE_MIN, min(1.0, sum(w) / len(w))))
    return out


def map_stroke(st, paper, dark, rep):
    """Samsung -> {'pen','color','width','pts'} w pt albo None (kreska bez punktów)."""
    if not st.pts:
        return None
    kind, known = pen_kind(st)
    size = (st.size if st.size else 5.0) * UNIT_PT
    color = st.color if st.color is not None else 0xFF000000
    pts = [(x * UNIT_PT, y * UNIT_PT, max(0.0, min(1.0, p))) for x, y, p, _t in st.pts]
    short = (st.pen or "?").split(".")[-1]

    if kind == "marker":
        mapped = highlighter_color_dark(invert_argb(color), paper) if dark else highlighter_color(color, paper)
        pen, width = mio.HIGHLIGHTER, size
        pts = [(x, y, 1.0) for x, y, _p in pts]
        rep.warn_once("marker-" + short, "Zakreślacz %s -> zakreślacz mNotes (stała szerokość %.1f pt, kolor przeliczony "
                      "tak, by na papierze dawał ten sam odcień co w Notatkach; krycie mNotes jest stałe)." % (short, width))
    elif kind == "brush":
        pen, width = mio.BRUSH, size * BRUSH_K
        pts = [(x, y, max(PRESSURE_MIN, min(1.0, brush_factor(p) / BRUSH_K))) for x, y, p in pts]
        mapped = color
        rep.warn_once("brush", "Pędzel %s -> pędzel mNotes; zmienna grubość odtworzona z nacisku (krzywa zmierzona na "
                      "próbkach, końce zbiegają tak jak w oryginale)." % short)
    elif kind == "ink":
        pen, width = mio.FOUNTAIN, size
        pts = [(x, y, f) for (x, y, _p), f in zip(pts, ink_factors(st))]
        mapped = color
        rep.warn_once("ink", "Pióro %s -> pióro wieczne mNotes; grubość zależy od prędkości pisania (wolno pełny "
                      "rozmiar, szybko do ok. 0,55 rozmiaru), tak jak w oryginale, który nie reaguje na nacisk "
                      "(krzywa zmierzona na kilkunastu kreskach, przybliżona)." % short)
    else:
        pen, width = mio.BALLPOINT, size
        pts = [(x, y, 1.0) for x, y, _p in pts]
        mapped = color
        rep.warn_once("pen-" + short, "Nieznane pióro %s -> długopis mNotes o stałej szerokości (przybliżenie, "
                      "brak kalibracji tego pióra)." % short)
    if dark and pen != mio.HIGHLIGHTER:
        mapped = invert_argb(mapped)
    return {"pen": pen, "color": mapped, "width": width,
            "pts": simplify(pts, pen in (mio.BRUSH, mio.FOUNTAIN))}


def simplify(pts, by_pressure):
    """Rzadsze próbki: gęste punkty z kwantyzacją 1/32 jednostki dałyby szum stycznych w konturze kreski."""
    if len(pts) <= 2:
        return pts
    out = [pts[0]]
    for q in pts[1:-1]:
        last = out[-1]
        if math.hypot(q[0] - last[0], q[1] - last[1]) >= MIN_STEP_PT or (by_pressure and abs(q[2] - last[2]) >= DP_PRESSURE_STEP):
            out.append(q)
    end = pts[-1]
    if math.hypot(end[0] - out[-1][0], end[1] - out[-1][1]) >= 1e-3:
        out.append(end)
    return out


# --- strona -------------------------------------------------------------------------------------

def fit_size(size_pt, offset_pt, pitch):
    """Najmniejszy rozmiar >= size_pt, przy którym siatka mNotes (wyśrodkowana) zaczyna się w offset_pt."""
    n = max(1, math.ceil((size_pt - 2 * offset_pt) / pitch - 1e-9))
    return n * pitch + 2 * offset_pt


def build_page_spec(pg, rep, args):
    w = pg.width * UNIT_PT
    h = pg.height * UNIT_PT
    paper = (pg.bg_color | 0xFF000000) if pg.bg_color is not None else 0xFFFFFFFF
    note = ""
    if args.paper is not None:
        paper = 0xFF000000 | args.paper
    elif args.dark:
        paper = invert_argb(paper)
    t = pg.template_type or 0
    template, spacing, line, lw = mio.TEMPLATE_BLANK, 0.0, None, 0.6
    if t in TEMPLATE_PITCH_FACTOR:
        kind = (t - 1) // 3  # 0 linie, 1 kratka, 2 kropki
        pitch = GRID_PITCH_PT * TEMPLATE_PITCH_FACTOR[t]
        template = (mio.TEMPLATE_LINED, mio.TEMPLATE_GRID, mio.TEMPLATE_DOTS)[kind]
        spacing = pitch
        if template == mio.TEMPLATE_GRID:
            w, h = fit_size(w, GRID_OFFSET_X_PT * (pitch / GRID_PITCH_PT), pitch), fit_size(h, GRID_OFFSET_Y_PT * (pitch / GRID_PITCH_PT), pitch)
            note = "rozmiar strony powiększony do %.1fx%.1f pt, by siatka mNotes (wyśrodkowana) zgodziła się z siatką Notatek" % (w, h)
        line, lw = (invert_argb(GRID_LINE) if args.dark else GRID_LINE), GRID_LINE_WIDTH_PT
        if t not in VERIFIED_TEMPLATES:
            rep.warn_once("tpl-%d" % t, "Wzór tła %d (%s) nie był sprawdzony na próbce: rozstaw i położenie linii są "
                          "przybliżone." % (t, ("wąskie linie", "średnie linie", "szerokie linie", "wąska kratka", "średnia kratka",
                                                 "szeroka kratka", "wąskie kropki", "średnie kropki", "szerokie kropki")[t - 1]))
        if note:
            rep.warn_once("fit", "Strona ze wzorem: " + note + " (siatka w mNotes nie ma przesunięcia).")
    elif t not in (0, None):
        rep.warn_once("tpl-%d" % t, "Wzór tła %d (%s) nie jest jeszcze odtwarzany: strona ma gładki papier." % (t, TEMPLATE_NAMES.get(t, "nieznany")))
    if pg.template_uri:
        rep.warn_once("uri", "Strona ma własny obraz tła (%s) — pominięty, strona ma gładki papier." % pg.template_uri)
    if getattr(pg, "pdf", None):
        rep.warn_once("pdf", "Strona ma wstawione tło PDF — pominięte w tym etapie (gładki papier).")
    if pg.bg_image is not None and not pg.template_uri:
        rep.warn_once("bgimg", "Strona ma obraz tła — pominięty w tym etapie.")
    spec = mio.page_spec(w, h, paper, template, line, spacing, lw, opts=0)
    return spec, paper


def convert_page(pg, strings, spec_paper, rep, args, stats):
    strokes = []
    for layer in pg.layers:
        for o in sdocx.walk(layer):
            if o.type == 1:
                try:
                    st = sdocx.parse_stroke(o.payload, strings)
                except Exception as e:  # noqa: BLE001 — jedna zepsuta kreska nie przerywa konwersji
                    stats["skipped"]["kreska nie do odczytania"] += 1
                    rep.warn_once("bad-" + type(e).__name__, "Kreska nie do odczytania (%s) — pominięta." % e)
                    continue
                m = map_stroke(st, spec_paper, args.dark, rep)
                if m:
                    strokes.append(m)
                    stats["strokes"] += 1
            elif o.type in (4, 100):
                continue  # kontenery: dzieci odwiedza walk()
            else:
                stats["skipped"][OBJECT_NAMES.get(o.type, "obiekt typu %d" % o.type)] += 1
    return strokes


def trim_trailing_empty(pages):
    """Strony bez żadnego obiektu na końcu odpadają (zostaje co najmniej jedna)."""
    n = len(pages)
    while n > 1 and not any(True for layer in pages[n - 1].layers for _o in sdocx.walk(layer)):
        n -= 1
    return pages[:n]


def default_title(path):
    stem = os.path.splitext(os.path.basename(path))[0]
    stem = re.sub(r"\s*\(\d+\)$", "", stem)
    stem = re.sub(r"_\d{6}_\d{6}$", "", stem)
    return stem.strip() or "Notatka Samsung Notes"


def convert(src, out, title=None, dark=False, paper=None):
    import tempfile
    args = argparse.Namespace(dark=dark, paper=paper)
    rep = Report()
    stats = {"strokes": 0, "skipped": Counter()}
    with tempfile.TemporaryDirectory() as tmp:
        strings, pages, _z = load_input(src, tmp)
    kept = trim_trailing_empty(pages)
    dropped = len(pages) - len(kept)
    if dropped:
        rep.note("Pominięto %d pustą stronę na końcu pliku: Samsung Notes dopisuje ją po ostatniej stronie z treścią "
                 "(w obu próbkach nie było jej ani w aplikacji, ani w PDF-ie). Jeśli to była celowa pusta strona, "
                 "dodaj ją w mNotes." % dropped)
    out_pages = []
    for pg in kept:
        spec, paper_c = build_page_spec(pg, rep, args)
        out_pages.append({"spec": spec, "strokes": convert_page(pg, strings, paper_c, rep, args, stats)})
    meta = mio.write_mnotes(out, title or default_title(src), out_pages)
    if dark:
        rep.note("Wygląd ciemny (--dark), jak w trybie ciemnym aplikacji Samsung Notes: papier i pismo mają odwróconą "
                 "jasność z zachowaniem barwy (sprawdzone na zrzucie ekranu: papier FCFCFC -> 030303, pismo czarne -> "
                 "białe), zakreślacz nałożony zwykłą alfą. Sam plik i PDF-y zawierają notatkę jasną; ciemność to "
                 "wygląd aplikacji. Linie wzoru na ciemnym papierze są wyliczone tą samą regułą, ale nie sprawdzone "
                 "na zrzucie.")
    for what, n in sorted(stats["skipped"].items()):
        rep.note("Pominięto: %s — %d szt. (jeszcze nie przenoszone)." % (what, n))
    head = ["Konwersja Samsung Notes -> mNotes", "Wejście: %s" % os.path.basename(src),
            "Wynik:  %s (%d stron, %d kresek)" % (os.path.basename(out), len(out_pages), stats["strokes"]), ""]
    text = "\n".join(head + ["Odstępstwa od oryginału:"] + ["  - " + l for l in rep.lines]) + "\n"
    return meta, text


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("input", help=".sdocx albo PDF zgodny z Notatkami")
    ap.add_argument("-o", "--out", help="plik wynikowy .mnotes (domyślnie obok wejścia)")
    ap.add_argument("--title", help="tytuł zeszytu (domyślnie z nazwy pliku)")
    ap.add_argument("--dark", action="store_true", help="wygląd jak w trybie ciemnym aplikacji Samsung Notes (papier i pismo z odwróconą jasnością)")
    ap.add_argument("--paper", help="wymuś kolor papieru, RRGGBB")
    a = ap.parse_args(argv)
    out = a.out or os.path.splitext(a.input)[0] + ".mnotes"
    paper = int(a.paper, 16) if a.paper else None
    _meta, text = convert(a.input, out, a.title, a.dark, paper)
    rpt = os.path.splitext(out)[0] + ".raport.txt"
    with open(rpt, "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Zapisano:", out)
    print("Raport:  ", rpt)


if __name__ == "__main__":
    main()
