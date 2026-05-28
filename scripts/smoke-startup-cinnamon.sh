#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-25}"
RUNTIME_HOME="$(mktemp -d)"
RUNTIME_DATA_HOME="$(mktemp -d)"
SMOKE_LOG_FILE="${SMOKE_LOG_FILE:-$RUNTIME_HOME/smoke-startup-cinnamon.log}"
RUNTIME_CACHE_HOME="$RUNTIME_DATA_HOME/cache"
RUNTIME_CONFIG_HOME="$RUNTIME_DATA_HOME/config"
mkdir -p "$RUNTIME_DATA_HOME/speedofsound"
mkdir -p "$RUNTIME_CACHE_HOME" "$RUNTIME_CONFIG_HOME"

cleanup() {
  rm -rf "$RUNTIME_HOME" "$RUNTIME_DATA_HOME"
}
trap cleanup EXIT

cat > "$RUNTIME_DATA_HOME/speedofsound/speedofsound.properties" <<'EOF'
default-language=en
secondary-language=es
welcome-screen-shown=true
text-output-method=clipboard
text-processing-enabled=false
selected-voice-model-provider-id=smoke-openai-asr
voice-model-providers=[{"id":"smoke-openai-asr","name":"Smoke OpenAI Provider","provider":"OPENAI","modelId":"gpt-4o-transcribe","credentialId":"smoke-credential"}]
credentials=[{"id":"smoke-credential","type":"API_KEY","name":"Smoke OpenAI","value":"dummy"}]
EOF
export HOME="$RUNTIME_HOME"
export XDG_DATA_HOME="$RUNTIME_DATA_HOME"
export XDG_CACHE_HOME="$RUNTIME_CACHE_HOME"
export XDG_CONFIG_HOME="$RUNTIME_CONFIG_HOME"
export SOS_DISABLE_GIO_STORE=true
export SOS_DISABLE_GSTREAMER=false
export SMOKE_TIMEOUT
export SMOKE_LOG_FILE

printf 'Running Cinnamon-compat startup smoke with timeout=%ss...\n' "$SMOKE_TIMEOUT"
./scripts/smoke-startup.sh "$SMOKE_TIMEOUT"

if rg -q "A fatal error was encountered during startup" "$SMOKE_LOG_FILE"; then
  echo "Cinnamon-compat smoke failed: unexpected startup fatal error"
  exit 1
fi
