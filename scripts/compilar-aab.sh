#!/usr/bin/env bash
# Genera y firma el Android App Bundle para Google Play.
# Usa el MISMO keystore local que la APK. Nunca contiene claves ni contraseñas.
set -Eeuo pipefail
umask 077
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[[ -d "$ANDROID_HOME/platforms/android-36" ]] || {
  echo "ERROR: falta Android SDK API 36 en $ANDROID_HOME" >&2
  echo 'Ejecutá primero ./scripts/instalar-sdk.sh' >&2
  exit 1
}

KEY="${XDG_DATA_HOME:-$HOME/.local/share}/essora-launcher/signing/essora-launcher-release.jks"
[[ -f "$KEY" ]] || {
  echo "ERROR: no está la firma existente: $KEY" >&2
  echo 'NO generes otra clave para actualizar Essora Launcher.' >&2
  exit 1
}
command -v jarsigner >/dev/null || { echo 'ERROR: falta jarsigner (JDK).' >&2; exit 1; }

./gradlew --no-daemon :app:bundleRelease
RAW_AAB="app/build/outputs/bundle/release/app-release.aab"
[[ -f "$RAW_AAB" ]] || { echo "ERROR: no se generó $RAW_AAB" >&2; exit 1; }

VERSION="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" app/build.gradle | head -n 1)"
[[ -n "$VERSION" ]] || { echo 'ERROR: no se pudo leer la versión.' >&2; exit 1; }
mkdir -p dist
OUT_AAB="dist/Essora-Launcher-${VERSION}.aab"
[[ ! -e "$OUT_AAB" ]] || { echo "ERROR: $OUT_AAB ya existe; movelo antes de volver a firmar." >&2; exit 1; }
trap 'rm -f "$OUT_AAB"' ERR

# AAB is signed with jarsigner (JAR signature), never with apksigner.
# The signer prompts for the private keystore password; it is not saved to disk.
echo 'Firmando el AAB con la clave existente de Essora Launcher...'
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore "$KEY" -signedjar "$OUT_AAB" "$RAW_AAB" essora-launcher

echo 'Verificando firma del Android App Bundle...'
VERIFY_LOG="$(mktemp)"
trap 'rm -f "$VERIFY_LOG"' EXIT
jarsigner -verify "$OUT_AAB" >"$VERIFY_LOG" 2>&1 || {
  cat "$VERIFY_LOG" >&2
  rm -f "$OUT_AAB"
  exit 1
}
if ! grep -q 'jar verified' "$VERIFY_LOG"; then
  cat "$VERIFY_LOG" >&2
  rm -f "$OUT_AAB"
  echo 'ERROR: jarsigner no confirmó la firma.' >&2
  exit 1
fi

echo
printf 'AAB FIRMADO Y VERIFICADO: %s\n' "$(pwd)/$OUT_AAB"
echo 'Publicá este AAB en Google Play Console, no la APK de GitHub.'
echo 'Atención: configurá Play App Signing con la clave de firma original si querés actualizaciones cruzadas con GitHub.'
