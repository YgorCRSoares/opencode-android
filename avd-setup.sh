#!/bin/bash
# Instala Android Emulator + imagem x86_64 (API 36) e cria o AVD de teste.
# Requer /dev/kvm ativo (SVM habilitado na BIOS).
set -euo pipefail

export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21
export ANDROID_HOME=/home/HyprKlavier/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

AVD_NAME="oc_test"
IMG_PKG="system-images;android-36;google_apis;x86_64"
DEVICE="pixel_7"

# --- pré-checagens -----------------------------------------------------------
if [ ! -e /dev/kvm ]; then
  echo "ERRO: /dev/kvm ausente — SVM não habilitado na BIOS" >&2
  exit 1
fi
echo "KVM OK: $(ls -l /dev/kvm | awk '{print $1, $3, $4}')"

# --- 1. pacotes do SDK -------------------------------------------------------
echo "=== Instalando emulator + sistema ==="
if sdkmanager --list_installed | grep -q "system-images;android-36;google_apis;x86_64"; then
  echo "Imagem já instalada, pulando"
else
  # || true: `yes` recebe SIGPIPE ao sdkmanager terminar (exit 141) e o
  # pipefail abortaria o script — o sucesso real é verificado logo abaixo.
  yes | sdkmanager "emulator" "$IMG_PKG" || true
  sdkmanager --list_installed | grep -q "system-images;android-36;google_apis;x86_64" \
    || { echo "ERRO: imagem do sistema não instalou" >&2; exit 1; }
fi
sdkmanager --list_installed | grep -E "emulator|system-images"

# --- 2. AVD ------------------------------------------------------------------
echo "=== Criando AVD $AVD_NAME ==="
if [ -d "$HOME/.android/avd/$AVD_NAME.avd" ]; then
  echo "AVD já existe, pulando"
else
  echo no | avdmanager create avd -n "$AVD_NAME" -k "$IMG_PKG" -d "$DEVICE" --force
fi
avdmanager list avd | grep -E "Name|Device|Target" | head -6

echo "AVD_SETUP_CONCLUIDO"
