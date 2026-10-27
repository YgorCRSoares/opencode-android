#!/bin/bash
# Build de verificação do toolchain Android (assembleDebug do app mínimo).
set -euo pipefail

export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21
export ANDROID_HOME=/home/HyprKlavier/android-sdk
export ANDROID_SDK_ROOT=/home/HyprKlavier/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:/home/HyprKlavier/.local/opt/gradle/bin:$PATH"

cd /home/HyprKlavier/opencode-android
echo "cwd=$(pwd)"
echo "settings presente: $(ls settings.gradle.kts)"
echo "java=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

gradle assembleDebug 2>&1 | tee /home/HyprKlavier/opencode-android/build.log
