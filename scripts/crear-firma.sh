#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
command -v keytool >/dev/null || { echo 'ERROR: falta keytool (JDK).' >&2; exit 1; }
private_dir="${XDG_DATA_HOME:-$HOME/.local/share}/essora-launcher/signing"
keystore="$private_dir/essora-launcher-release.jks"
if [[ -e "$keystore" ]]; then
  echo "La firma ya existe: $keystore"
  echo 'NO se generará otra. Conservar la clave original es imprescindible para actualizar.'
  exit 0
fi
mkdir -p -m 700 "$private_dir"
chmod 700 "$private_dir"
echo 'Creando firma permanente de Essora Launcher (una sola vez).'
echo 'keytool solicitará una contraseña en esta terminal; no se guardará en el ZIP.'
keytool -genkeypair \
  -keystore "$keystore" \
  -storetype PKCS12 \
  -alias essora-launcher \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -dname 'CN=Essora Launcher, O=Essora, C=AR'
chmod 600 "$keystore"
echo
printf 'Firma creada: %s\n' "$keystore"
echo 'Hacé dos copias de seguridad seguras de esta clave y guardá su contraseña.'
echo 'Nunca compartas ni subas el archivo .jks.'
