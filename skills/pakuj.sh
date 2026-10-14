#!/usr/bin/env bash
# Pakuje skill do ZIP-a gotowego do wgrania w Claude (folder skilla na najwyższym poziomie archiwum).
set -euo pipefail
cd "$(dirname "$0")"
rm -f zadania-do-mnotes.zip
find zadania-do-mnotes -name __pycache__ -prune -o -type f -print | sort | zip -q -@ zadania-do-mnotes.zip
unzip -l zadania-do-mnotes.zip
