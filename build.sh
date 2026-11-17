#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/.android-sdk}"
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

./gradlew testDebugUnitTest assembleDebug

echo "==> Build complete: $SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ls -lh "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
