#!/bin/bash
# Sets up Android builds in Claude Code cloud sessions: installs the Android SDK
# and downloads Gradle dependencies, so ./gradlew :app:assembleDebug works.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK=/opt/android-sdk
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  tmp=$(mktemp -d)
  curl -fsSLo "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# Matches compileSdk/targetSdk in app/build.gradle.kts.
if [ ! -d "$SDK/platforms/android-35" ] || [ ! -d "$SDK/build-tools/35.0.0" ] || [ ! -d "$SDK/platform-tools" ]; then
  yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
  "$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null
fi

echo "export ANDROID_HOME=$SDK" >> "$CLAUDE_ENV_FILE"
echo "sdk.dir=$SDK" > "$CLAUDE_PROJECT_DIR/local.properties"

# Download Gradle dependencies now, and Robolectric's Android jar (fetched by the first
# app unit test). Maven Central sometimes answers 429 (too many requests); what was
# already fetched is kept, so retrying gets further each time.
cd "$CLAUDE_PROJECT_DIR"
for attempt in 1 2 3 4 5 6; do
  if ANDROID_HOME=$SDK ./gradlew -q :app:assembleDebug :core:compileTestKotlin \
      :app:testDebugUnitTest --tests '*TvPlayerNavigationTest*' > /dev/null 2>&1; then
    exit 0
  fi
  echo "Gradle attempt $attempt failed, retrying" >&2
  sleep 20
done
echo "Gradle dependencies could not all be downloaded; builds will fetch the rest" >&2
