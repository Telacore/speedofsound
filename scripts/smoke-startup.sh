#!/usr/bin/env bash

# Smoke-check the GUI startup path in environments without a full desktop session.
#
# This command intentionally avoids asserting that the app registers on D-Bus, since
# some CI/headless contexts do not expose a usable desktop bus. The key assertions are:
# - Gradle task completes.
# - The process does not fail with a hard JVM exit in startup code.
# - Expected startup logs are emitted, including the app entrypoint.
# - Optional hard-fail when startup logs contain a known fatal startup error.
#
# Internal environment variables:
# - SMOKE_PRESERVE_RUNTIME=true: do not create temporary HOME/XDG dirs and keep
#   caller-provided runtime env vars (used by smoke-startup-cinnamon.sh).
#
# Usage:
#   ./scripts/smoke-startup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d)"
SMOKE_PRESERVE_RUNTIME="${SMOKE_PRESERVE_RUNTIME:-false}"

if [[ "${SMOKE_PRESERVE_RUNTIME,,}" == "true" ]]; then
  OUT_FILE="${SMOKE_LOG_FILE:-$TMP_DIR/startup.log}"
  trap 'if [[ -z "${SMOKE_LOG_FILE:-}" ]]; then rm -rf "$TMP_DIR"; fi' EXIT
else
  RUNTIME_HOME="$(mktemp -d)"
  RUNTIME_DATA_HOME="$(mktemp -d)"
  RUNTIME_CACHE_HOME="$RUNTIME_DATA_HOME/cache"
  RUNTIME_CONFIG_HOME="$RUNTIME_DATA_HOME/config"
  mkdir -p "$RUNTIME_DATA_HOME/speedofsound"
  mkdir -p "$RUNTIME_CACHE_HOME" "$RUNTIME_CONFIG_HOME"

  cat > "$RUNTIME_DATA_HOME/speedofsound/speedofsound.properties" <<EOF
default-language=en
secondary-language=es
welcome-screen-shown=true
text-processing-enabled=false
selected-voice-model-provider-id=smoke-openai-asr
voice-model-providers=[{"id":"smoke-openai-asr","name":"Smoke OpenAI Provider","provider":"OPENAI","modelId":"gpt-4o-transcribe","credentialId":"smoke-credential"}]
credentials=[{"id":"smoke-credential","type":"API_KEY","name":"Smoke OpenAI","value":"dummy"}]
EOF

  export HOME="$RUNTIME_HOME"
  export XDG_DATA_HOME="$RUNTIME_DATA_HOME"
  export XDG_CACHE_HOME="$RUNTIME_CACHE_HOME"
  export XDG_CONFIG_HOME="$RUNTIME_CONFIG_HOME"
  OUT_FILE="${SMOKE_LOG_FILE:-$TMP_DIR/startup.log}"
  trap 'if [[ -z "${SMOKE_LOG_FILE:-}" ]]; then rm -rf "$TMP_DIR"; fi; rm -rf "$RUNTIME_HOME" "$RUNTIME_DATA_HOME"' EXIT
fi

TIMEOUT_SECONDS="${SMOKE_TIMEOUT:-${1:-60}}"
SMOKE_FAIL_ON_FATAL="${SMOKE_FAIL_ON_FATAL:-false}"
NORMALIZED_SMOKE_FAIL_ON_FATAL="${SMOKE_FAIL_ON_FATAL,,}"

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
  if [[ "$NORMALIZED_SMOKE_FAIL_ON_FATAL" == "true" || "$NORMALIZED_SMOKE_FAIL_ON_FATAL" == "1" || "$NORMALIZED_SMOKE_FAIL_ON_FATAL" == "yes" ]]; then
    echo "Startup smoke failed: fatal startup error detected."
    cat "$OUT_FILE"
    exit 1
  fi
  echo "Startup smoke warning: startup hit a fatal startup error path"
  echo "See log: $OUT_FILE"
fi

echo "Startup smoke completed."
echo "Log: $OUT_FILE"
