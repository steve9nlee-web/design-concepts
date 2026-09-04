#!/usr/bin/env bash
# Install just enough Android SDK to build this app, with no GUI.
#
# For a cloud session whose network policy allows dl.google.com. It is also
# fine on a Linux or macOS laptop that has a JDK but no Android Studio.
#
# Not tested end to end: the environment this was written in had dl.google.com
# blocked, which is the whole reason the script exists. The sequence is the
# standard one, but treat the first run as the real test.
#
#   bash tools/install-android-sdk.sh && ./gradlew assembleDebug

set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
# Bump these two together when compileSdk changes in app/build.gradle.kts.
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

case "$(uname -s)" in
  Darwin) CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" ;;
esac

echo "==> SDK root: $SDK_ROOT"
mkdir -p "$SDK_ROOT/cmdline-tools"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Downloading command-line tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  # The zip contains a top-level "cmdline-tools" directory, but sdkmanager
  # insists on living at cmdline-tools/latest/. Getting this nesting wrong is
  # the single most common reason sdkmanager reports no packages available.
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
else
  echo "==> Command-line tools already present"
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"

echo "==> Accepting licences"
# Every licence prompt wants a literal "y". Without this the installs below
# hang forever waiting on stdin.
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true

echo "==> Installing platform-tools, $PLATFORM, $BUILD_TOOLS"
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" "$PLATFORM" "$BUILD_TOOLS"

# Gradle finds the SDK through local.properties. It is deliberately gitignored,
# because the path is specific to whoever ran this.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$REPO_ROOT/local.properties"
echo "==> Wrote $REPO_ROOT/local.properties"

echo
echo "Done. Now:"
echo "  ./gradlew test           # 21 unit tests, no device needed"
echo "  ./gradlew assembleDebug  # APK -> app/build/outputs/apk/debug/"
