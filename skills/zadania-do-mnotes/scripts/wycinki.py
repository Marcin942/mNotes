#!/usr/bin/env python3
"""Zapisuje po jednym obrazku PNG na zadanie (cała treść: wstęp i wszystkie podpunkty) na podstawie plan.json.

Użycie: python3 wycinki.py plan.json [--out KATALOG] [--dpi 130]
Obrazki służą do spisania treści zadań (krok 5 w SKILL.md): model ogląda je po jednym i przepisuje tekst.
Tekst z warstwy PDF bywa zniekształcony (wzory, polskie znaki), więc nie jest podstawą spisu.
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zm_common as zc  # noqa: E402
import build  # noqa: E402


def slug(s):
    s = re.sub(r"[^0-9A-Za-ząćęłńóśźżĄĆĘŁŃÓŚŹŻ]+", "-", s).strip("-")
    return s[:40] or "zadanie"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("plan")
    ap.add_argument("--out", default=None, help="katalog wyników (domyślnie wycinki/ obok planu)")
    ap.add_argument("--dpi", type=int, default=130)
    a = ap.parse_args()
    plan = build.load_plan(a.plan)
    out = a.out or os.path.join(os.path.dirname(os.path.abspath(a.plan)), "wycinki")
    os.makedirs(out, exist_ok=True)
    rend = build.Renderer(plan["pdf"], a.dpi)
    n = 0
    for i, t in enumerate(plan["tasks"], 1):
        segs = list(t.get("intro") or [])
        for s in t.get("subs") or []:
            segs += s.get("segs") or []
        if not segs:
            continue
        im = rend.crop(segs, plan["x_range"])
        path = os.path.join(out, "%02d-%s.png" % (i, slug(t["label"])))
        im.save(path, "PNG", optimize=True)
        zc.say(path)
        n += 1
    zc.say("Zapisano %d obrazków zadań w %s" % (n, out))


if __name__ == "__main__":
    main()
