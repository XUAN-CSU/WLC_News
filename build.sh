#!/usr/bin/env bash
# Build the WLC_News Android app.
# Usage:
#   ./build.sh            -> debug APK
#   ./build.sh release    -> release (unsigned) APK
set -e
cd "$(dirname "$0")"

# Force the full JDK we installed. The system Java on this machine
# (/usr/lib/jvm/java-21-openjdk-amd64) is runtime-only and cannot compile.
export JAVA_HOME="$HOME/.jdks/temurin21"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "❌ Full JDK not found at $JAVA_HOME" >&2
  echo "   Reinstall it, or edit JAVA_HOME at the top of this script." >&2
  exit 1
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/bin:$PATH"

case "${1:-debug}" in
  debug)
    echo "Building DEBUG APK…"
    gradle -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleDebug
    OUT="$PWD/app/build/outputs/apk/debug/app-debug.apk"
    ;;
  release)
    echo "Building RELEASE (unsigned) APK…"
    gradle -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease
    OUT="$PWD/app/build/outputs/apk/release/app-release-unsigned.apk"
    ;;
  *)
    echo "Unknown mode '$1'. Use: ./build.sh  (debug) | ./build.sh release" >&2
    exit 2
    ;;
esac

if [ -f "$OUT" ]; then
  echo ""
  echo "✔ Build OK"
  echo "  APK: $OUT"
  echo ""
  echo "To install on a phone connected by USB (debugging on):"
  echo "  adb install -r \"$OUT\""
else
  echo "Build finished, but the APK was not found at: $OUT" >&2
  exit 1
fi
