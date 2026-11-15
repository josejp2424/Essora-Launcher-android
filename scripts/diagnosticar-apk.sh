#!/usr/bin/env bash
# Diagnóstico local del paquete instalado, permisos y certificado.
# No cambia ajustes ni instala/desinstala nada.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
VERSION="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" app/build.gradle | head -n 1)"
[[ -n "$VERSION" ]] || { echo 'No se pudo leer versionName desde app/build.gradle' >&2; exit 1; }
APK="${1:-dist/Essora-Launcher-${VERSION}.apk}"
[[ -f "$APK" ]] || { echo "No existe la APK: $APK" >&2; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
TOOLS="$SDK/build-tools/35.0.0"
AAPT="${AAPT:-$TOOLS/aapt}"
SIGNER="${APKSIGNER:-$TOOLS/apksigner}"
echo '== APK =='
sha256sum "$APK"
echo
if [[ -x "$AAPT" ]]; then
  echo '== Identificador, versión y permisos declarados =='
  "$AAPT" dump badging "$APK" | grep -E "^(package:|sdkVersion:|targetSdkVersion:|uses-permission:)" || true
  "$AAPT" dump permissions "$APK"
else
  echo "aapt no disponible en $AAPT"
fi
echo
if [[ -x "$SIGNER" ]]; then
  echo '== Verificación de firma y certificado =='
  "$SIGNER" verify --verbose --print-certs "$APK"
else
  echo "apksigner no disponible en $SIGNER"
fi
if command -v adb >/dev/null 2>&1 && adb get-state 2>/dev/null | grep -q '^device$'; then
  echo
  echo '== Launchers registrados en Android =='
  adb shell cmd role holders android.app.role.HOME || true
  echo '== Versión instalada de Essora Launcher =='
  adb shell dumpsys package org.essora.launcher | grep -m 2 -E 'versionCode=|versionName=' || true
else
  echo
  echo 'ADB no está conectado. Si conectás tu teléfono por USB con depuración activada, podrá comprobar qué launcher es el predeterminado.'
fi
