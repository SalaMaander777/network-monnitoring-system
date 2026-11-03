#!/usr/bin/env bash
# Подготовка офлайн-репозитория: нужен интернет (architecture.md §3).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." >/dev/null && pwd)"
cd "$ROOT"
mvn -B -Dmaven.repo.local=offline/m2 verify
