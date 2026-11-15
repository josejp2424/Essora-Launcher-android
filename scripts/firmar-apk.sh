#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
if [[ $# -ne 2 ]]; then
  echo 'Uso: firmar-apk.sh app-release-unsigned.apk essora-launcher-firmada.apk' >&2
  exit 2
fi
for tool in zipalign apksigner; do
  command -v "$tool" >/dev/null || { echo "ERROR: falta $tool (Android SDK Build Tools)." >&2; exit 1; }
done
source_apk="$1"
output_apk="$2"
[[ -f "$source_apk" ]] || { echo 'ERROR: falta APK de origen.' >&2; exit 1; }
if [[ -e "$output_apk" ]]; then
  echo "ERROR: no sobrescribiré el archivo de salida: $output_apk" >&2
  exit 1
fi
private_dir="${XDG_DATA_HOME:-$HOME/.local/share}/essora-launcher/signing"
keystore="$private_dir/essora-launcher-release.jks"
[[ -f "$keystore" ]] || { echo "ERROR: la clave oficial no existe: $keystore" >&2; exit 1; }
if apksigner verify "$source_apk" >/dev/null 2>&1; then
  echo 'ERROR: la APK de origen ya está firmada. Usá una APK release unsigned.' >&2
  exit 1
fi
mkdir -p "$(dirname "$output_apk")"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
aligned="$workdir/aligned.apk"
zipalign -f 4 "$source_apk" "$aligned"
apksigner sign --ks "$keystore" --ks-key-alias essora-launcher --out "$output_apk" "$aligned"
echo 'Verificando la firma oficial...'
output="$(apksigner verify --verbose --print-certs "$output_apk")"
fingerprint="$(printf '%s\n' "$output" | sed -nE 's/^[[:space:]]*Signer #1 certificate SHA-256 digest:[[:space:]]*([0-9a-fA-F]+).*/\1/p' | head -1 | tr '[:upper:]' '[:lower:]')"
[[ -n "$fingerprint" ]] || { echo 'ERROR: no se pudo obtener huella de firma.' >&2; rm -f "$output_apk"; exit 1; }
expected_file="$private_dir/certificado.sha256"
if [[ -f "$expected_file" ]]; then
  expected="$(tr -cd '[:xdigit:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')"
  if [[ "$fingerprint" != "$expected" ]]; then
    echo 'ERROR: certificado distinto del usado anteriormente.' >&2
    rm -f "$output_apk"
    exit 1
  fi
else
  printf '%s\n' "$fingerprint" > "$expected_file"
  chmod 600 "$expected_file"
  echo "Huella oficial inicial guardada: $expected_file"
fi
printf '%s\n' "$output"
printf 'APK firmada: %s\n' "$output_apk"
