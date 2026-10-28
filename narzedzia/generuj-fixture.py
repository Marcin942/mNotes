#!/usr/bin/env python3
"""Generuje pliki testowe dla SkillZadaniaFormatTest (app/src/test/resources/zadania/) kodem skilla.

Test w Kotlinie czyta je prawdziwym kodem aplikacji, więc łapie rozjazd między skryptami a formatem mNotes.
Uruchom ponownie tylko wtedy, gdy zmienia się format zapisu w skillu.
"""
import json
import os
import sys
import zipfile
import tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "zadania-do-mnotes", "scripts"))
import zm_common as zc  # noqa: E402

OUT = os.path.join(os.path.dirname(ROOT), "app", "src", "test", "resources", "zadania")


def main():
    os.makedirs(OUT, exist_ok=True)
    pages = [
        {"spec": zc.pdf_background_spec(420.0, 594.01, "orig.pdf", 0), "images": []},
        {"spec": zc.grid_spec(), "images": [("a.png", 210.0, 20.0, 420.0, 40.0), ("b.png", 210.0, 75.5, 420.0, 71.0)]},
    ]
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "x.mnotes")
        zc.write_mnotes(path, "Zadania ąę", pages, {"orig.pdf": b"%PDF-", "a.png": b"x", "b.png": b"y"})
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
