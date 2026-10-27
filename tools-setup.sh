#!/bin/bash
# Instala as ferramentas Android para o projeto opencode-android.
# - JDK 21 (Temurin) em ~/.local/opt/jdk-21        (AGP/Gradle não suportam JDK 26 como JVM de build)
# - Android cmdline-tools em ~/android-sdk/cmdline-tools/latest
# - Gradle 8.14.3 em ~/.local/opt/gradle -> gradle-8.14.3
# - Pacotes do SDK: platform-tools, platform android-36, build-tools 36.0.0 (fallback 35)
set -euo pipefail

OPT="$HOME/.local/opt"
SDK="$HOME/android-sdk"
TMP="$HOME/.cache/android-tools-setup"

CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-8.14.3-bin.zip"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"

mkdir -p "$OPT" "$SDK" "$TMP"
cd "$TMP"

log() { printf '\n=== %s ===\n' "$*"; }

# --- 1. JDK 21 ---------------------------------------------------------------
if [ -x "$OPT/jdk-21/bin/java" ]; then
  log "JDK 21 já presente, pulando"
else
  log "Baixando JDK 21 (Temurin)"
  curl -fL --retry 3 -o jdk21.tar.gz "$JDK_URL"
  mkdir -p "$OPT/jdk-21"
  tar -xzf jdk21.tar.gz -C "$OPT/jdk-21" --strip-components=1
fi
export JAVA_HOME="$OPT/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

# --- 2. Android cmdline-tools ------------------------------------------------
if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "cmdline-tools já presentes, pulando"
else
  log "Baixando Android cmdline-tools"
  curl -fL --retry 3 -o cmdline-tools.zip "$CMDLINE_URL"
  rm -rf "$TMP/cmdline-extract"
  mkdir -p "$TMP/cmdline-extract"
  unzip -q cmdline-tools.zip -d "$TMP/cmdline-extract"
  rm -rf "$SDK/cmdline-tools/latest"
  mkdir -p "$SDK/cmdline-tools"
  mv "$TMP/cmdline-extract/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager --version

# --- 3. Gradle ---------------------------------------------------------------
if [ -x "$OPT/gradle/bin/gradle" ]; then
  log "Gradle já presente, pulando"
else
  log "Baixando Gradle 8.14.3"
  curl -fL --retry 3 -o gradle.zip "$GRADLE_URL"
  rm -rf "$OPT/gradle-8.14.3"
  unzip -q gradle.zip -d "$OPT"
  ln -sfn "$OPT/gradle-8.14.3" "$OPT/gradle"
fi
export PATH="$OPT/gradle/bin:$PATH"

# --- 4. Licenças + pacotes do SDK -------------------------------------------
log "Aceitando licenças do SDK"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

log "Instalando pacotes do SDK"
sdkmanager "platform-tools"

if sdkmanager "platforms;android-36" "build-tools;36.0.0"; then
  :
else
  log "android-36 indisponível, usando android-35"
  sdkmanager "platforms;android-35" "build-tools;35.0.0"
fi

# --- 5. Verificação final ----------------------------------------------------
log "Pacotes instalados"
sdkmanager --list_installed

log "Verificando ferramentas"
[ -d "$SDK/platforms/android-36" ] || [ -d "$SDK/platforms/android-35" ] || { echo "ERRO: nenhuma platform instalada"; exit 1; }
[ -x "$SDK/build-tools" ] || { echo "ERRO: build-tools ausente"; exit 1; }
gradle --version | head -8
echo
echo "INSTALACAO CONCLUIDA"
