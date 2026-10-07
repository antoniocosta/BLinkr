#!/usr/bin/env bash
# Dev helper. Uses the SDK/JDK/emulator from android-dev-toolkit
# (default ../android-dev-toolkit; override with TOOLKIT=/path).
#
#   scripts/dev.sh build        assembleDebug
#   scripts/dev.sh install      installDebug on the emulator (emulator-5554)
#   scripts/dev.sh run          install + launch the app
#   scripts/dev.sh test         JVM unit tests
#   scripts/dev.sh phone        release build (R8) installed on the USB phone for user 0
#   scripts/dev.sh log          logcat for the app only
#   scripts/dev.sh approve      approve all of BLinkr's links (what "Open by default → Add links" does)
#   scripts/dev.sh links        show BLinkr's link approval state
#   scripts/dev.sh open URL     open a link the way another app would (to test routing)
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLKIT="${TOOLKIT:-$(cd .. && pwd)/android-dev-toolkit}"
[ -f "$TOOLKIT/setup/env.sh" ] || { echo "android-dev-toolkit not found at $TOOLKIT (set TOOLKIT=)" >&2; exit 1; }
. "$TOOLKIT/setup/env.sh"

# Point Gradle at the toolkit SDK (local.properties is gitignored)
grep -qs "^sdk.dir=$ANDROID_SDK_ROOT\$" local.properties || echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
export ANDROID_SERIAL="$EMU_SERIAL"
PKG=net.uncorp.blinkr

case "${1:-build}" in
  build)   ./gradlew assembleDebug ;;
  install) ./gradlew installDebug ;;
  run)     ./gradlew installDebug && emu_adb shell am start -n "$PKG/.MainActivity" ;;
  test)    ./gradlew testDebugUnitTest ;;
  phone)   ./gradlew assembleRelease
           phone=$(adb devices | awk 'NR>1 && $2=="device" && $1!~/^emulator-/ {print $1; exit}')
           [ -n "$phone" ] || { echo "no USB phone found (adb devices)" >&2; exit 1; }
           adb -s "$phone" install -r --user 0 app/build/outputs/apk/release/app-release.apk ;;
  log)     emu_adb logcat --pid="$(emu_adb shell pidof -s $PKG | tr -d '\r')" ;;
  approve) emu_adb shell pm set-app-links-user-selection --user 0 --package "$PKG" true all ;;
  links)   emu_adb shell pm get-app-links --user 0 "$PKG" ;;
  open)    emu_adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "'${2:?URL}'" ;;
  *)       sed -n '2,13p' "$0"; exit 1 ;;
esac
