#!/usr/bin/env bash
# Pakuje skille do ZIP-ów gotowych do wgrania w Claude (folder skilla na najwyższym poziomie archiwum).
# Bez argumentów pakuje wszystkie skille; z argumentem tylko wskazany (np. samsung-notes-do-mnotes).
set -euo pipefail
cd "$(dirname "$0")"
SKILLE=(zadania-do-mnotes samsung-notes-do-mnotes)
[ $# -gt 0 ] && SKILLE=("$@")
for s in "${SKILLE[@]}"; do
  rm -f "$s.zip"
  find "$s" -name __pycache__ -prune -o -type f -print | sort | zip -q -@ "$s.zip"
  unzip -l "$s.zip"
done
