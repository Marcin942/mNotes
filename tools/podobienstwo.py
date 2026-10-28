#!/usr/bin/env python3
"""Porównuje kod mNotes z obcym materiałem (np. kod pracy licencjackiej): wspólne linie, literały i 8-gramy tokenów.

Użycie: python3 tools/podobienstwo.py <katalog-materiału> [-v <plik-mNotes>]
Identyfikatory są sprowadzane do małych liter bez podkreśleń, więc camelCase z Kotlina spotyka snake_case z Pythona;
tłumaczenia linia po linii między językami i tak mogą umknąć.
"""
import os
import re
import subprocess
import sys
from collections import defaultdict

MN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = sys.argv[1]
EXT_SRC = (".py", ".js", ".html", ".css", ".kt")
EXT_MN = (".kt", ".kts", ".py", ".sh", ".xml")

TOK = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|\d+(?:\.\d+)?|\S")
STR = re.compile(r"\"([^\"\n]{14,})\"|'([^'\n]{14,})'")
KEYW = set("""def return if else elif for while in and or not import from class self this val var fun let const
function new true false none null true false is as try except catch finally with lambda pass break continue
private public override data object when package int float double string list map""".split())


def files(root, exts, tracked=False):
    if tracked:
        out = subprocess.run(["git", "-C", root, "ls-files"], capture_output=True, text=True).stdout.split()
        return [os.path.join(root, p) for p in out if p.endswith(exts)]
    res = []
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith(exts):
                res.append(os.path.join(d, f))
    return res


def norm_line(s):
    s = re.sub(r"\s+", " ", s.strip().lower())
    s = s.strip(";{}(),")
    return s


def tokens(text):
    # identyfikatory sprowadzone do małych liter bez podkreśleń, żeby camelCase i snake_case się spotkały
    out = []
    for t in TOK.findall(text):
        if re.match(r"[A-Za-z_]", t):
            t = t.replace("_", "").lower()
            if t in KEYW:
                continue
            out.append(t)
        elif re.match(r"\d", t):
            out.append(t)
    return out


def read(p):
    try:
        with open(p, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


src_files = files(SRC, EXT_SRC)
mn_files = [p for p in files(MN, EXT_MN, tracked=True) if not os.path.abspath(p).startswith(os.path.abspath(SRC) + os.sep)]

src_lines = defaultdict(set)
src_strs = defaultdict(set)
src_grams = defaultdict(set)
N = 8
for p in src_files:
    t = read(p)
    rel = os.path.relpath(p, SRC)
    for ln in t.splitlines():
        n = norm_line(ln)
        if len(n) >= 30 and len(set(n)) > 10:
            src_lines[n].add(rel)
    for m in STR.finditer(t):
        s = (m.group(1) or m.group(2)).strip()
        if len(set(s)) > 6:
            src_strs[s].add(rel)
    tk = tokens(t)
    for i in range(len(tk) - N + 1):
        g = tuple(tk[i:i + N])
        if len(set(g)) >= 5:
            src_grams[g].add(rel)

hits_l, hits_s, hits_g = [], [], defaultdict(int)
for p in mn_files:
    t = read(p)
    rel = os.path.relpath(p, MN)
    for ln in t.splitlines():
        n = norm_line(ln)
        if n in src_lines:
            hits_l.append((rel, n, sorted(src_lines[n])[:2]))
    for m in STR.finditer(t):
        s = (m.group(1) or m.group(2)).strip()
        if s in src_strs:
            hits_s.append((rel, s, sorted(src_strs[s])[:2]))
    tk = tokens(t)
    for i in range(len(tk) - N + 1):
        g = tuple(tk[i:i + N])
        if g in src_grams:
            hits_g[rel] += 1

print("pliki źródła:", len(src_files), "pliki mNotes:", len(mn_files))
print("wspólne linie (>=30 znaków):", len(hits_l))
for h in hits_l[:40]:
    print("  L", h)
print("wspólne literały (>=14 znaków):", len(hits_s))
for h in hits_s[:40]:
    print("  S", h)
print("pliki mNotes ze wspólnymi 8-gramami tokenów:", len(hits_g))
for rel, c in sorted(hits_g.items(), key=lambda x: -x[1])[:20]:
    print("  G", c, rel)
if "-v" in sys.argv:
    want = sys.argv[sys.argv.index("-v") + 1]
    t = read(os.path.join(MN, want))
    tk = tokens(t)
    for i in range(len(tk) - N + 1):
        g = tuple(tk[i:i + N])
        if g in src_grams:
            print("   ", " ".join(g), "<-", sorted(src_grams[g])[:1])
