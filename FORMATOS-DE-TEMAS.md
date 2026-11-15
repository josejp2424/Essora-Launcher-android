# Essora Theme Creator — Formatos de intercambio

Essora Launcher permite crear, compartir e instalar temas sin instalar otra APK. Para importar o exportar se utiliza el selector de archivos y el panel de compartir de Android.

## Temas `.essoratheme`

Un archivo `.essoratheme` es un ZIP de datos, no contiene código ejecutable. Incluye:

```text
theme.json        (obligatorio)
LICENSE           (GNU GPL-3.0, obligatorio)
CREDITS.txt       (autor original, modificaciones y créditos)
wallpaper.webp    (opcional)
preview.webp      (opcional)
icons/*.webp      (opcional)
icons/*.png       (opcional al importar)
```

`theme.json` contiene `format="essoratheme"`, `formatVersion=1`, `license="GPL-3.0-only"`, `name`, `author`, `version`, `description`, `originalAuthor`, `modifiedBy`, `website`, `wallpaperCredit`, `iconsCredit`, `colors` e `icons`.

Los colores se guardan como valores ARGB: `border`, `background`, `dock`, `text`, `battery`, `batteryOutline`, `accent`. Los iconos pueden asociarse a un componente Android (`paquete/.Actividad`) o a un paquete (`org.mozilla.firefox`).

Si un tema usa un icon pack ADW/Nova instalado, el campo `iconPackPackage` guarda la referencia. **No se copia la APK ni sus iconos al tema exportado**: quien instale el tema deberá disponer de ese pack. Los iconos externos conservan sus propias licencias; por ejemplo, las ilustraciones de Arcticons utilizan CC BY-SA 4.0.

La importación valida los nombres y rutas de los archivos, los tamaños, la licencia y los recursos. Los temas se almacenan de manera privada en el dispositivo. Al editar un tema de otra persona se mantiene el autor original y se identifica al modificador.

## Compartir, exportar e instalar

- **Compartir tema**: en `Configuración → Creador de temas → Mis temas`, elegí el tema y tocá **Compartir tema**. Android ofrecerá las aplicaciones instaladas que puedan recibir el archivo (por ejemplo, Telegram o WhatsApp).
- **Exportar**: guarda una copia `.essoratheme` en una carpeta elegida por el usuario sin abrir el panel de compartir.
- **Instalar tema local**: desde `Configuración → Temas`, abre el selector de archivos de Android para buscar el `.essoratheme` o ZIP en Descargas u otra carpeta. El nombre, autor y licencia se muestran antes de instalar.

No se crea un enlace web público automáticamente: se envía el archivo a la aplicación seleccionada. El proveedor de contenido de Essora concede acceso temporal de lectura para compartir.

## Packs de iconos `.essoraicons` o `.zip`

Los packs propios de Essora incluyen `icons.json`, `LICENSE`, `CREDITS.txt` e imágenes PNG/WebP dentro de `icons/`.

```json
{
  "format": "essoraicons",
  "formatVersion": 1,
  "license": "GPL-3.0-only",
  "author": "Essora",
  "icons": {
    "com.android.settings": "icons/settings.webp"
  }
}
```

Se incluye `examples/Essora-Iconos-Ejemplo.essoraicons`. Al importar un ZIP propio, sus iconos se incorporan al tema que se está editando, no a las aplicaciones del sistema.

Essora también puede usar packs ADW/Nova compatibles ya instalados en Android, como Arcticons White o Black, sin descomprimir sus APK. Si el pack no contiene un icono, se muestra el original. Se pueden elegir imágenes individuales para aplicaciones concretas.

## Licencia y créditos

Los temas y packs propios exportados desde Essora deben declarar `GPL-3.0-only` y conservar los créditos. La licencia del tema no cambia automáticamente los derechos de wallpapers o iconos ajenos: para compartirlos el usuario debe contar con autorización compatible.
