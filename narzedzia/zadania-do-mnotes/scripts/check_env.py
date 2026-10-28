#!/usr/bin/env python3
"""Sprawdza środowisko skilla i mówi, co zadziała. Użycie: python3 check_env.py

Wymagane: Python 3 + Pillow + jedno narzędzie do renderowania PDF (pdftoppm z poppler, PyMuPDF, ghostscript lub mutool).
Do przeskalowania oryginałów do A5 (w .mnotes i w .pdf) potrzebny jest backend skalujący: pypdf, PyMuPDF lub ghostscript.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zm_common as zc  # noqa: E402
import pdfout  # noqa: E402


def main():
    ok = True
    print("Python %s" % sys.version.split()[0])
    pil = zc.have_module("PIL")
    print("Pillow:        %s" % ("jest" if pil else "BRAK (pip install pillow)"))
    ok &= pil
    print("numpy:         %s" % ("jest (szybciej)" if zc.have_module("numpy") else "brak (działa wolniej, to nie błąd)"))
    renders = [n for n, have in (("pdftoppm", zc.which("pdftoppm")), ("PyMuPDF", zc.import_fitz()),
                                 ("ghostscript", zc.which("gs")), ("mutool", zc.which("mutool"))) if have]
    print("render PDF:    %s" % (", ".join(renders) if renders else "BRAK (zainstaluj poppler-utils albo pip install pymupdf)"))
    ok &= bool(renders)
    text = bool(zc.which("pdftotext")) or zc.import_fitz() is not None
    print("tekst z PDF:   %s" % ("jest" if text else "BRAK (potrzebny pdftotext albo PyMuPDF; pip install pymupdf)"))
    ok &= text
    be = pdfout.scale_backend()
    print("skalowanie PDF: %s" % (be if be else "brak — oryginały nie zostaną zmniejszone do A5, .pdf niemożliwy (pip install pypdf)"))
    print("katalog wyników: %s" % zc.default_out_dir())
    print("WYNIK: %s" % ("gotowe" if ok else "brakuje wymaganych narzędzi (patrz wyżej)"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
