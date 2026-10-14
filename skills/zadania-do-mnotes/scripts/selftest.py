#!/usr/bin/env python3
"""Samotest skilla: buduje syntetyczny PDF z zadaniami, analizuje go i składa .mnotes (i .pdf, jeśli się da).

Użycie: python3 selftest.py   (kod wyjścia 0 = wszystko działa). Nie wymaga żadnych plików wejściowych.
"""
import os
import subprocess
import sys
import tempfile
import zipfile
import json

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import zm_common as zc  # noqa: E402


def make_pdf(path):
    """Minimalny PDF A4 (czcionka Helvetica) z dwoma zadaniami: jedno z podpunktami, drugie bez."""
    lines = [
        (72, 760, "Zadania domowe 1"),
        (72, 700, "Zadanie 1. Oblicz granice nastepujacych ciagow dla n rosnacego do nieskonczonosci,"),
        (72, 686, "korzystajac z twierdzenia o trzech ciagach."),
        (90, 650, "(a) lim n/(n+1)"),
        (90, 610, "(b) lim (n+2)/(n^2+1)"),
        (90, 570, "(c) lim sin(n)/n"),
        (72, 500, "Zadanie 2. Udowodnij, ze kazdy ciag zbiezny jest ograniczony."),
        (72, 440, "Zadanie 3. Podaj przyklad ciagu ograniczonego i rozbieznego."),
    ]
    content = "BT /F1 12 Tf\n" + "".join("1 0 0 1 %d %d Tm (%s) Tj\n" % (x, y, t) for x, y, t in lines) + "ET"
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            ("<< /Length %d >>\nstream\n%s\nendstream" % (len(content), content)).encode("latin-1")]
    out = bytearray(b"%PDF-1.4\n")
    offs = []
    for i, o in enumerate(objs, 1):
        offs.append(len(out))
        out += b"%d 0 obj\n" % i + o + b"\nendobj\n"
    xr = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    for o in offs:
        out += b"%010d 00000 n \n" % o
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xr)
    with open(path, "wb") as f:
        f.write(out)


def run(args):
    r = subprocess.run([sys.executable] + args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return r.returncode, r.stdout.decode("utf-8", "replace")


def main():
    with tempfile.TemporaryDirectory() as d:
        pdf = os.path.join(d, "probka.pdf")
        make_pdf(pdf)
        code, out = run([os.path.join(HERE, "analyze.py"), pdf, "--out", d])
        print(out)
        if code != 0:
            print("SAMOTEST: analiza nie powiodła się")
            sys.exit(1)
        plan = json.load(open(os.path.join(d, "plan.json"), encoding="utf-8"))
        shape = [(t["label"], [s["label"] for s in t["subs"]]) for t in plan["tasks"]]
        expect = [("Zadanie 1.", ["(a)", "(b)", "(c)"]), ("Zadanie 2.", []), ("Zadanie 3.", [])]
        if [(a.strip(), b) for a, b in shape] != expect:
            print("SAMOTEST: zły podział zadań:", shape)
            sys.exit(1)
        code, out = run([os.path.join(HERE, "build.py"), os.path.join(d, "plan.json"), "--format", "both", "--out-dir", d])
        print(out)
        if code != 0:
            print("SAMOTEST: budowanie nie powiodło się")
            sys.exit(1)
        with zipfile.ZipFile(os.path.join(d, "probka.mnotes")) as z:
            meta = json.loads(z.read("meta.json"))
        if len(meta["pages"]) != 1 + 3 + 2:
            print("SAMOTEST: zła liczba stron:", len(meta["pages"]))
            sys.exit(1)
    print("SAMOTEST: OK")


if __name__ == "__main__":
    main()
