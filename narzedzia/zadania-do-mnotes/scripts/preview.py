#!/usr/bin/env python3
"""Rysuje podglądy (ramki wycinków + linijka w pt) na podstawie istniejącego plan.json.

Użycie: python3 preview.py plan.json [--out KATALOG]
Służy do sprawdzenia planu po ręcznej poprawce (analyze.py rysuje podglądy tylko przy własnej analizie).
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zm_common as zc  # noqa: E402
import analyze  # noqa: E402


class _Pg:
    def __init__(self, idx, w, h, img):
        self.idx, self.w, self.h, self.img = idx, w, h, img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("plan")
    ap.add_argument("--out", default=None, help="katalog podglądów (domyślnie obok planu)")
    a = ap.parse_args()
    with open(a.plan, encoding="utf-8") as f:
        plan = json.load(f)
    out = a.out or os.path.dirname(os.path.abspath(a.plan))
    os.makedirs(out, exist_ok=True)
    pages = []
    for i, (w, h) in enumerate(plan["page_sizes"]):
        pages.append(_Pg(i, w, h, zc.render_page(plan["pdf"], i + 1, 115)))
    for f in analyze.draw_previews(plan["pdf"], pages, plan, out):
        zc.say(f)


if __name__ == "__main__":
    main()
