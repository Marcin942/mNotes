#!/usr/bin/env python3
"""Samotest skilla: syntetyczny .sdocx -> konwersja -> odczyt .mnotes. Nie wymaga żadnych plików wejściowych.

Użycie: python3 selftest.py   (kod wyjścia 0 = wszystko działa, ostatnia linia „SAMOTEST: OK”).
"""
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import convert  # noqa: E402
import mnotes_io as mio  # noqa: E402
import synth_sdocx as synth  # noqa: E402

failures = []


def check(cond, what):
    print(("  ok   " if cond else "  BŁĄD ") + what)
    if not cond:
        failures.append(what)


def near(a, b, tol):
    return abs(a - b) <= tol


def main():
    with tempfile.TemporaryDirectory() as d:
        raw = synth.build_sdocx(synth.sample_pages())
        sd = os.path.join(d, "probka.sdocx")
        pdf = os.path.join(d, "probka.pdf")
        std = os.path.join(d, "standardowy.pdf")
        with open(sd, "wb") as f:
            f.write(raw)
        with open(pdf, "wb") as f:
            f.write(synth.wrap_in_pdf(raw))
        with open(std, "wb") as f:
            f.write(b"%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")

        out = os.path.join(d, "probka.mnotes")
        _meta, report = convert.convert(sd, out, "Próbka ąę")
        print(report)
        meta, pages = mio.read_mnotes(out)
        check(meta["title"] == "Próbka ąę", "tytuł zeszytu przeszedł")
        check(len(pages) == 2, "dwie strony")

        p1, p2 = pages
        s1 = p1["spec"]
        check(s1["t"] == mio.TEMPLATE_GRID and near(s1["s"], 38.25, 0.01), "strona 1: kratka co 38,25 pt")
        check(s1["p"] == mio.signed32(0xFFE6E6E6), "strona 1: papier E6E6E6")
        check(s1["w"] >= 600.0 and s1["h"] >= 848.0, "strona 1: nie mniejsza niż oryginał (%.1fx%.1f)" % (s1["w"], s1["h"]))
        check(p2["spec"]["t"] == mio.TEMPLATE_BLANK and p2["spec"]["p"] == mio.signed32(0xFFFCFCFC),
              "strona 2: gładki papier FCFCFC")

        st = p1["strokes"]
        check([s["pen"] for s in st] == [mio.FOUNTAIN, mio.FOUNTAIN, mio.BRUSH, mio.HIGHLIGHTER],
              "strona 1: pióro, pióro, pędzel, zakreślacz")
        check(len(p2["strokes"]) == 1 and p2["strokes"][0]["pen"] == mio.FOUNTAIN, "strona 2: jedno pióro")

        slow, fast, brush, marker = st
        mid = lambda s: [p for _x, _y, p in s["pts"]][len(s["pts"]) // 3: -len(s["pts"]) // 3 or None]
        check(near(slow["width"], 16.77 * convert.UNIT_PT, 0.01), "pióro: szerokość = rozmiar * 0,375")
        check(all(p > 0.95 for p in mid(slow)), "pióro pisane wolno: pełna grubość")
        check(all(0.5 <= p <= 0.65 for p in mid(fast)), "pióro pisane szybko: ok. 0,55-0,6 rozmiaru")
        check(slow["color"] == mio.signed32(0xFF000000) and fast["color"] == mio.signed32(0xFF1565C0), "kolory piór")
        ps = [p for _x, _y, p in brush["pts"]]
        check(near(brush["width"], 19.88 * convert.UNIT_PT * convert.BRUSH_K, 0.01), "pędzel: szerokość z rozmiaru")
        check(ps[-1] > ps[0] + 0.3, "pędzel: grubość rośnie z naciskiem")
        check(near(marker["width"], 31.0 * convert.UNIT_PT, 0.01) and all(p == 1.0 for _x, _y, p in marker["pts"]),
              "zakreślacz: stała szerokość")
        check((marker["color"] >> 24) & 0xFF == 0xFF, "zakreślacz: kolor nieprzezroczysty (krycie daje mNotes)")
        dense = [(i * 0.1, 0.0, 1.0) for i in range(101)]
        thin = convert.simplify(dense, False)
        check(thin[0] == dense[0] and thin[-1] == dense[-1] and 10 < len(thin) < 50,
              "gęste próbki są rzedzone, końce zostają (%d ze 101)" % len(thin))
        check(all(0.0 <= x <= s1["w"] and 0.0 <= y <= s1["h"] for s in st for x, y, _p in s["pts"]),
              "kreski mieszczą się na stronie")

        out2 = os.path.join(d, "z-pdf.mnotes")
        convert.convert(pdf, out2, "Próbka ąę")
        _m2, pages2 = mio.read_mnotes(out2)
        same = [[(s["pen"], s["color"], s["width"], s["pts"]) for s in p["strokes"]] for p in pages] == \
               [[(s["pen"], s["color"], s["width"], s["pts"]) for s in p["strokes"]] for p in pages2]
        check(same, "PDF zgodny z Notatkami daje to samo co .sdocx")

        try:
            convert.convert(std, os.path.join(d, "x.mnotes"))
            check(False, "zwykły PDF bez osadzonej notatki jest odrzucany")
        except SystemExit as e:
            check("PDF" in str(e), "zwykły PDF bez osadzonej notatki jest odrzucany z komunikatem")

        out3 = os.path.join(d, "ciemna.mnotes")
        convert.convert(sd, out3, "Ciemna", dark=True)
        _m3, pages3 = mio.read_mnotes(out3)
        check(pages3[0]["spec"]["p"] == mio.signed32(convert.DARK_PAPER), "--dark: ciemny papier")
        check(pages3[0]["strokes"][0]["color"] == mio.signed32(0xFFFFFFFF), "--dark: czarne pismo staje się białe")

        try:
            import numpy  # noqa: F401
            import PIL  # noqa: F401
            import preview
            img = preview.render_page(pages[0], 1.0)
            check(img.size == (round(s1["w"]), round(s1["h"])), "podgląd PNG ma rozmiar strony")
            check(len({img.getpixel((x, 300)) for x in range(100, 400, 7)}) > 1, "podgląd zawiera kreski")
        except ImportError:
            print("  (pominięto podgląd: brak Pillow albo numpy)")

    if failures:
        print("SAMOTEST: BŁĘDY (%d)" % len(failures))
        sys.exit(1)
    print("SAMOTEST: OK")


if __name__ == "__main__":
    main()
