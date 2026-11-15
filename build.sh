#!/usr/bin/env bash
# Compila la APK desde esta carpeta. Por defecto genera release firmado con la firma permanente.
set -Eeuo pipefail
cd "$(dirname "$0")"
MODE="${1:-release}"
case "$MODE" in
  --aab)
    exec ./scripts/compilar-aab.sh ;;
  --both)
    ./build.sh release
    exec ./scripts/compilar-aab.sh ;;
  release|--debug) ;;
  *) echo 'Uso: ./build.sh [--debug|--aab|--both]' >&2; exit 2 ;;
esac
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[[ -d "$ANDROID_HOME/platforms/android-36" ]] || {
  echo "Falta Android SDK API 36 en $ANDROID_HOME" >&2
  echo 'Ejecutá primero ./scripts/instalar-sdk.sh' >&2
  exit 1
}
if [[ "$MODE" == --debug ]]; then
  ./gradlew --no-daemon :app:assembleDebug
  echo 'APK de prueba (paquete separado org.essora.launcher.debug): app/build/outputs/apk/debug/app-debug.apk'
  echo 'No uses esta APK como versión oficial, tiene firma de depuración.'
  exit 0
fi
KEY="${XDG_DATA_HOME:-$HOME/.local/share}/essora-launcher/signing/essora-launcher-release.jks"
[[ -f "$KEY" ]] || { echo 'Falta la firma permanente. Ejecutá una sola vez: ./scripts/crear-firma.sh' >&2; exit 1; }
export PATH="$ANDROID_HOME/build-tools/35.0.0:$PATH"
for tool in zipalign apksigner; do command -v "$tool" >/dev/null || {
  echo "Falta $tool. Ejecutá ./scripts/instalar-sdk.sh" >&2; exit 1; }
done
./gradlew --no-daemon :app:assembleRelease
INPUT="app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$INPUT" ]] || { echo "No se encontró la APK sin firma: $INPUT" >&2; exit 1; }
mkdir -p dist
# Read the version directly from Gradle to prevent mismatches on later releases.
VERSION="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" app/build.gradle | head -n 1)"
[[ -n "$VERSION" ]] || { echo 'ERROR: no se pudo leer versionName de app/build.gradle' >&2; exit 1; }
OUTPUT="dist/Essora-Launcher-${VERSION}.apk"
rm -f "$OUTPUT"
./scripts/firmar-apk.sh "$INPUT" "$OUTPUT"
echo
echo "APLICACIÓN LISTA: $(pwd)/$OUTPUT"
