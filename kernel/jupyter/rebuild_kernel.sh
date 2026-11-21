#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-$REPO_ROOT/gradlew}"
if [[ ! -x "$GRADLE_BIN" ]]; then
  GRADLE_BIN="$(command -v gradle || true)"
fi

usage() {
  cat <<'EOF'
Usage:
  ./rebuild_kernel.sh doctor
  ./rebuild_kernel.sh build
  ./rebuild_kernel.sh fat
  ./rebuild_kernel.sh install [--user|--prefix PATH]

The Gradle :kernel:jupyter project is the canonical build. Maven and the old
manual classpath assembly path are intentionally retired during migration.
EOF
}

doctor() {
  echo "JavaLand Jupyter kernel doctor"
  echo "  repository: $REPO_ROOT"
  echo "  module:     $SCRIPT_DIR"
  echo "  gradle:     ${GRADLE_BIN:-missing}"
  echo "  jar:        $SCRIPT_DIR/build/libs/javaland-kernel.jar"
  [[ -x "$GRADLE_BIN" ]] || echo "  warning: Gradle executable not found"
}

run_gradle() {
  if [[ -z "$GRADLE_BIN" || ! -x "$GRADLE_BIN" ]]; then
    echo "Gradle executable not found. Use the repository wrapper or install Gradle."
    exit 1
  fi
  cd "$REPO_ROOT"
  "$GRADLE_BIN" --no-daemon "$@"
}

command="${1:-doctor}"
case "$command" in
  doctor) doctor ;;
  build) run_gradle :kernel:jupyter:build ;;
  fat) run_gradle :kernel:jupyter:fatJar ;;
  install)
    shift
    run_gradle :kernel:jupyter:fatJar
    python3 "$SCRIPT_DIR/install.py" "$@"
    ;;
  help|-h|--help) usage ;;
  *) echo "Unknown command: $command"; usage; exit 2 ;;
esac
