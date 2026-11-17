#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
IME_ID="gg.lebe.mp02keyboard/.ime.Mp02InputMethodService"
ADB_BIN="${ADB_BIN:-$(command -v adb || echo "$HOME/.android-sdk/platform-tools/adb")}"

if [ ! -f "$APK_PATH" ]; then
  "$SCRIPT_DIR/build.sh"
fi

TARGET="${MP02_ADB_TARGET:-}"
if [ -n "$TARGET" ]; then
  "$ADB_BIN" connect "$TARGET" || true
  sleep 1.5
fi

if [ -z "$TARGET" ] && ! "$ADB_BIN" devices | grep -q '[[:space:]]device$'; then
  LAST_TARGET_FILE="$HOME/.android/last_adb_target"
  if [ -f "$LAST_TARGET_FILE" ]; then
    TARGET="$(cat "$LAST_TARGET_FILE")"
    "$ADB_BIN" connect "$TARGET" || true
    sleep 1.5
  fi
fi

ADB_ARGS=()
if [ -n "$TARGET" ] && "$ADB_BIN" devices | grep -q "^${TARGET}[[:space:]]*device$"; then
  ADB_ARGS=(-s "$TARGET")
fi

"$ADB_BIN" "${ADB_ARGS[@]}" install --user 0 -r -d "$APK_PATH"
sleep 1.5
"$ADB_BIN" "${ADB_ARGS[@]}" shell ime enable --user 0 "$IME_ID"
"$ADB_BIN" "${ADB_ARGS[@]}" shell ime set --user 0 "$IME_ID"
echo "==> Installed and activated $IME_ID for user 0."
