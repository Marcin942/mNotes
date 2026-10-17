#!/usr/bin/env python3
"""Generuje pliki testowe dla SkillSamsungFormatTest (app/src/test/resources/samsung/) kodem skilla.

Wejściem jest syntetyczny .sdocx (`synth_sdocx.sample_pages()`), więc fixture nie zawiera żadnych prywatnych danych.
Test w Kotlinie czyta wynik prawdziwym kodem aplikacji, więc łapie rozjazd między skryptami a formatem mNotes.
Uruchom ponownie tylko wtedy, gdy zmienia się format zapisu albo kalibracja w skillu.
"""
import json
import os
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "samsung-notes-do-mnotes", "scripts"))
import convert  # noqa: E402
import synth_sdocx as synth  # noqa: E402

OUT = os.path.join(os.path.dirname(ROOT), "app", "src", "test", "resources", "samsung")


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        src = os.path.join(d, "probka.sdocx")
        with open(src, "wb") as f:
            f.write(synth.build_sdocx(synth.sample_pages()))
        path = os.path.join(d, "probka.mnotes")
        convert.convert(src, path, "Próbka Samsung ąę")
        with zipfile.ZipFile(path) as z:
            meta = json.loads(z.read("meta.json"))
            with open(os.path.join(OUT, "meta.json"), "w", encoding="utf-8") as f:
                json.dump(meta, f, ensure_ascii=False, indent=1)
            for i, p in enumerate(meta["pages"]):
                with open(os.path.join(OUT, "strona%d.mnp" % i), "wb") as f:
                    f.write(z.read("p/%s.mnp" % p["id"]))
    print("OK:", OUT)


if __name__ == "__main__":
    main()
