#!/usr/bin/env bash
set -Eeuo pipefail
if [[ $# -ne 1 ]]; then
  echo 'Uso: verificar-firma.sh aplicacion-firmada.apk' >&2
  exit 2
fi
command -v apksigner >/dev/null || { echo 'ERROR: falta apksigner (Android SDK Build Tools).' >&2; exit 1; }
apk="$1"
[[ -f "$apk" ]] || { echo "ERROR: no existe: $apk" >&2; exit 1; }
printf 'Verificando APK: %s\n' "$apk"
output="$(apksigner verify --verbose --print-certs "$apk")"
printf '%s\n' "$output"
current="$(printf '%s\n' "$output" | sed -nE 's/^[[:space:]]*Signer #1 certificate SHA-256 digest:[[:space:]]*([0-9a-fA-F]+).*/\1/p' | head -1 | tr '[:upper:]' '[:lower:]')"
[[ -n "$current" ]] || { echo 'ERROR: no se pudo leer la huella de certificado.' >&2; exit 1; }
private_dir="${XDG_DATA_HOME:-$HOME/.local/share}/essora-launcher/signing"
expected_file="$private_dir/certificado.sha256"
if [[ -f "$expected_file" ]]; then
  expected="$(tr -cd '[:xdigit:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')"
  if [[ "$current" != "$expected" ]]; then
    echo 'ERROR: esta APK NO tiene el certificado oficial de Essora Launcher.' >&2
    echo "Esperado: $expected" >&2
    echo "Encontrado: $current" >&2
    exit 1
  fi
  echo 'OK: firma válida y certificado idéntico al registrado.'
else
  echo "Firma válida, pero NO hay huella de referencia en $expected_file"
  echo 'La referencia se guarda automáticamente al firmar la primera APK con firmar-apk.sh.'
fi
