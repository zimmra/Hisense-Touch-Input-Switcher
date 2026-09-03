#!/usr/bin/env bash
# Sideload Input Switcher onto the Hisense 75WE3FE over ADB.
#
#   ./deploy.sh              install the release APK and open the setup screen
#   ./deploy.sh --build      run ./gradlew assembleRelease first
#   ./deploy.sh --debug      install the debug APK instead of release
#   ./deploy.sh --logs       tail the app's logcat after installing
#   ./deploy.sh --uninstall  remove the app
#
# One-time on the display: Settings → Developer options → USB debugging (or Wireless debugging).
# For Wireless debugging, pair/connect first:  adb connect <display-ip>:5555
set -euo pipefail

cd "$(dirname "$0")"

PKG="com.zimindustries.inputswitcher"
VARIANT="release"
BUILD=0
LOGS=0
UNINSTALL=0

for arg in "$@"; do
    case "$arg" in
        --build) BUILD=1 ;;
        --debug) VARIANT="debug" ;;
        --logs) LOGS=1 ;;
        --uninstall) UNINSTALL=1 ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

command -v adb >/dev/null || { echo "adb not found on PATH (install Android platform-tools)" >&2; exit 1; }

echo "== devices"
adb devices -l
if [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)" -eq 0 ]; then
    echo "no device in 'device' state. Check USB debugging / adb connect and authorise the prompt on the display." >&2
    exit 1
fi

if [ "$UNINSTALL" -eq 1 ]; then
    echo "== uninstalling $PKG"
    adb uninstall "$PKG"
    exit 0
fi

APK="app/build/outputs/apk/$VARIANT/input-switcher-$VARIANT.apk"

if [ "$BUILD" -eq 1 ]; then
    echo "== building $VARIANT"
    ./gradlew "assemble${VARIANT^}"
fi

[ -f "$APK" ] || { echo "$APK not found. Run ./gradlew assemble${VARIANT^} or pass --build." >&2; exit 1; }

echo "== installing $APK"
# -r: replace existing (same signing key, so data and the placed widget survive)
# -g: grant runtime permissions (none requested today; harmless)
adb install -r -g "$APK"

echo "== opening setup screen"
adb shell am start -n "$PKG/.SetupActivity"

cat <<EOF

Next, on the display:
  * Tap "Conference Hub" in the app. The panel must switch to HDMI 1. If not, see README "If the
    direct transport does not work".
  * Tap "Add widget to home screen" and confirm, or long-press the home screen → Widgets → Input
    Switcher → drag to place.
  * "Add shortcuts to home screen" pins the three per-input icons, or drag them from the app drawer.

Logs:   adb logcat -s InputSwitcher     (add -d to dump and exit)
Update: adb install -r $APK
Remove: adb uninstall $PKG
EOF

if [ "$LOGS" -eq 1 ]; then
    echo "== logcat (Ctrl-C to stop)"
    adb logcat -s InputSwitcher
fi
