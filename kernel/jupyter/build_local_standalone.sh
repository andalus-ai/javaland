#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-$REPO_ROOT/gradlew}"
if [[ ! -x "$GRADLE_BIN" ]]; then GRADLE_BIN="$(command -v gradle || true)"; fi
if [[ -z "$GRADLE_BIN" || ! -x "$GRADLE_BIN" ]]; then
  echo "Gradle executable not found. Use the repository wrapper or install Gradle."
  exit 1
fi
cd "$REPO_ROOT"
"$GRADLE_BIN" --no-daemon :kernel:jupyter:fatJar
