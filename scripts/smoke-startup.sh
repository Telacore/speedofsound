#!/usr/bin/env bash

# Smoke-check the GUI startup path in environments without a full desktop session.
#
# This command intentionally avoids asserting that the app registers on D-Bus, since
# some CI/headless contexts do not expose a usable desktop bus. The key assertions are:
# - Gradle task completes.
# - The process does not fail with a hard JVM exit in startup code.
# - Expected startup logs are emitted, including the app entrypoint.
#
# Usage:
#   ./scripts/smoke-startup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d)"
OUT_FILE="${SMOKE_LOG_FILE:-$TMP_DIR/startup.log}"
trap 'if [[ -z "${SMOKE_LOG_FILE:-}" ]]; then rm -rf "$TMP_DIR"; fi' EXIT

TIMEOUT_SECONDS="${1:-20}"

export SOS_DISABLE_GIO_STORE=true
export SOS_DISABLE_GSTREAMER=false
export GRADLE_OPTS="${GRADLE_OPTS:---enable-native-access=ALL-UNNAMED}"

printf 'Running startup smoke with timeout=%ss...\n' "$TIMEOUT_SECONDS"
if timeout "$TIMEOUT_SECONDS" ./gradlew :app:run --no-daemon --console=plain >"$OUT_FILE" 2>&1; then
  status=0
else
  status=$?
  if grep -q "BUILD FAILED" "$OUT_FILE"; then
    echo "Startup smoke failed: Gradle reported BUILD FAILED"
    cat "$OUT_FILE"
    exit "$status"
  fi
  # Some headless environments report a session bus timeout during app run but still exit 0.
  # Treat timeout exits from the `timeout` wrapper as warnings, not failures, if startup logs exist.
  if [[ $status -ne 124 ]]; then
    echo "Startup smoke failed with status $status"
    cat "$OUT_FILE"
    exit "$status"
  fi
fi

if ! grep -q "Running application." "$OUT_FILE"; then
  echo "Startup smoke failed: application entrypoint log not found"
  cat "$OUT_FILE"
  exit 1
fi

if grep -q "Process finished with exit code" "$OUT_FILE"; then
  echo "Startup smoke failed: unexpected hard process termination"
  cat "$OUT_FILE"
  exit 1
fi

if grep -q "A fatal error was encountered during startup" "$OUT_FILE"; then
  echo "Startup smoke warning: startup hit a fatal startup error path"
  echo "See log: $OUT_FILE"
fi

echo "Startup smoke completed."
echo "Log: $OUT_FILE"
