#!/usr/bin/env bash
set -Eeuo pipefail
# Instala Android SDK localmente, sin sudo, después de aceptar las licencias.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
TOOLS="$SDK/cmdline-tools/latest/bin/sdkmanager"
URL=https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
SHA=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
command -v java >/dev/null || { echo 'Necesitás instalar un JDK (recomendado: openjdk-21-jdk).' >&2; exit 1; }
command -v unzip >/dev/null || { echo 'Falta unzip.' >&2; exit 1; }
if [[ ! -x "$TOOLS" ]]; then
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  echo "Descargando Android Command Line Tools (~182 MB) en $SDK..."
  if command -v curl >/dev/null; then curl --fail --location --retry 2 "$URL" -o "$work/tools.zip"
  elif command -v wget >/dev/null; then wget -O "$work/tools.zip" "$URL"
  else echo 'Necesitás curl o wget.' >&2; exit 1; fi
  echo "$SHA  $work/tools.zip" | sha256sum -c -
  unzip -q "$work/tools.zip" -d "$work"
  mkdir -p "$SDK/cmdline-tools"
  mv "$work/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
printf '\nPara usar el SDK de Android, primero revisá y aceptá sus licencias:\n'
"$TOOLS" --sdk_root="$SDK" --licenses
printf '\nInstalando paquetes de compilación Android 16...\n'
"$TOOLS" --sdk_root="$SDK" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
cat > "$ROOT/local.properties" <<LOCAL
sdk.dir=$SDK
LOCAL
printf '\nSDK listo en %s\n' "$SDK"
printf 'Ahora ejecutá: ./scripts/crear-firma.sh (una sola vez) y luego ./build.sh\n'
