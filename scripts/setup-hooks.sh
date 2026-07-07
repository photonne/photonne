#!/bin/sh
# Activa los git hooks versionados del repo (auto-bump de versión, etc.).
# core.hooksPath es config local (no se clona), así que cada clon nuevo debe
# ejecutar esto una vez:
#
#   ./scripts/setup-hooks.sh
#
set -e
REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"
git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true
echo "Hooks activados: core.hooksPath -> .githooks"
