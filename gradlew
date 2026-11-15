#!/usr/bin/env bash
# Portable Gradle bootstrap; no global Gradle installation needed.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VER=8.13
HASH=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL="$HOME_DIR/essora-distributions/gradle-$VER"
if [[ ! -x "$INSTALL/bin/gradle" ]]; then
  for tool in unzip sha256sum; do command -v "$tool" >/dev/null || { echo "Falta $tool" >&2; exit 1; }; done
  mkdir -p "$(dirname "$INSTALL")"
  TEMP="$(mktemp -d)"
  trap 'rm -rf "$TEMP"' EXIT
  URL="https://services.gradle.org/distributions/gradle-$VER-bin.zip"
  echo "Descargando Gradle $VER por primera vez..."
  if command -v curl >/dev/null; then curl --fail --location --retry 2 "$URL" -o "$TEMP/gradle.zip"
  elif command -v wget >/dev/null; then wget -O "$TEMP/gradle.zip" "$URL"
  else echo 'Necesitás curl o wget.' >&2; exit 1; fi
  echo "$HASH  $TEMP/gradle.zip" | sha256sum -c -
  unzip -q "$TEMP/gradle.zip" -d "$TEMP"
  mv "$TEMP/gradle-$VER" "$INSTALL"
fi
exec "$INSTALL/bin/gradle" -p "$HERE" "$@"
