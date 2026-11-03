#!/usr/bin/env bash
# Сборка и тесты без сети из offline/m2 (architecture.md §3).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." >/dev/null && pwd)"
cd "$ROOT"
PGV="$(sed -n 's:.*<pg.binaries.version>\(.*\)</pg.binaries.version>.*:\1:p' pom.xml | head -n1)"
DIR="offline/m2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/$PGV"
if ! compgen -G "$DIR/*.jar" >/dev/null; then
  echo "Нет jar в $DIR: сначала scripts/prepare-offline.sh" >&2
  exit 1
fi
mvn -B -o -Dmaven.repo.local=offline/m2 verify
