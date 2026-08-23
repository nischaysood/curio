#!/usr/bin/env bash
#
# Curio — build, install, launch, tail logs. One command, no IDE.
#
#   ./run.sh            build + install + launch + logs
#   ./run.sh --emu      boot the emulator first, then the above
#   ./run.sh --test     run the unit tests only (no device needed)
#   ./run.sh --logs     just tail logs from the running app
#
# Requires the one-time setup in README (JAVA_HOME, ANDROID_HOME, an AVD named
# "curio"). Everything below is deliberately explicit rather than clever, so a
# failure tells you which step broke.

set -euo pipefail

APP_ID="app.curio"
ACTIVITY="$APP_ID/.MainActivity"
AVD_NAME="curio"

# AGP 8.7.3 will not run on JDK 26, which Homebrew installs as a Gradle dependency.
# Pin to 17 here so it works regardless of what the shell happens to have.
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

cd "$(dirname "$0")"

say() { printf "\n\033[1;33m▸ %s\033[0m\n" "$1"; }

boot_emulator() {
  if adb devices | grep -q "emulator.*device$"; then
    say "Emulator already running"
    return
  fi
  say "Booting $AVD_NAME"
  emulator -avd "$AVD_NAME" -no-snapshot-load -netdelay none -netspeed full >/dev/null 2>&1 &
  say "Waiting for boot (first boot can take 2-3 min)"
  adb wait-for-device
  # sys.boot_completed flips well before the launcher is actually usable;
  # installing too early fails with INSTALL_FAILED_* for no obvious reason.
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
  sleep 3
  say "Emulator ready"
}

tail_logs() {
  say "Logs — Ctrl+C to stop (the app keeps running)"
  # Crashes surface as AndroidRuntime:E. Compose problems usually appear
  # under System.err or the Compose tag.
  adb logcat -c 2>/dev/null || true
  adb logcat AndroidRuntime:E System.err:W Compose:D "$APP_ID":V *:S
}

case "${1:-}" in
  --test)
    say "Unit tests"
    ./gradlew :composeApp:testDebugUnitTest
    exit 0
    ;;
  --logs)
    tail_logs
    exit 0
    ;;
  --emu)
    boot_emulator
    ;;
esac

# The emulator dies whenever the Mac sleeps, and adb keeps a stale entry for it —
# so `adb devices` lists something that no longer exists and the build gets all
# the way to install before failing. Clear the stale state before trusting it.
adb reconnect offline >/dev/null 2>&1 || true

if ! adb devices | grep -qE "(device|emulator)-?.*\sdevice$"; then
  say "Nothing connected — starting the emulator"
  boot_emulator
fi

say "Building and installing"
./gradlew :composeApp:installDebug

say "Launching"
adb shell am start -n "$ACTIVITY" >/dev/null

tail_logs
