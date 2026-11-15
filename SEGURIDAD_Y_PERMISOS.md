# Seguridad y permisos — Essora Launcher

Essora Launcher es un launcher Android de código abierto. El proyecto no incluye ni publica claves de firma, credenciales, datos personales ni APK firmadas.

## Permisos y funciones

- **CAMERA**: se solicita en tiempo de ejecución para encender o apagar la linterna mediante un gesto configurable (doble toque por defecto); el launcher no toma fotografías ni graba vídeos por esa función.
- **SET_WALLPAPER**: se utiliza únicamente si el usuario decide establecer un fondo de pantalla del sistema desde Essora.
- **HOME**: Android ofrece su propio selector para establecer el launcher predeterminado.
- **Acceso a archivos**: los fondos e iconos se importan con el selector de documentos de Android, sin pedir acceso general al almacenamiento.
- **Paquetes de iconos**: `<queries>` permite descubrir packs ADW/Nova conocidos sin solicitar `QUERY_ALL_PACKAGES`.
- **Compartir**: se usa `ACTION_SEND` y un proveedor privado para dar acceso temporal de lectura al archivo `.essoratheme` compartido.
- **Colaborar con Essora**: solo cuando el usuario toca esa opción, `ACTION_VIEW` abre `https://paypal.me/essoralinux` en una aplicación compatible. No se integra SDK de pagos ni se solicitan permisos extra; cualquier aportación es opcional.

Los temas personalizados se guardan en el almacenamiento de la aplicación. Su importación valida rutas, tamaños, imágenes y metadatos. Las imágenes de terceros solo deben redistribuirse con permiso compatible; los créditos se conservan.

## Claves privadas y compilaciones

La firma oficial se mantiene fuera del proyecto, en el directorio privado del usuario, mediante los scripts de compilación y firma. No subir a GitHub archivos `*.jks`, certificados privados, contraseñas, `local.properties`, `dist/`, APK compiladas ni otras credenciales. Para poder actualizar una APK existente se debe conservar la firma original.

## Google Play Protect

El proyecto aún no está disponible en Google Play y sigue en pruebas. Al instalar una APK obtenida fuera de Play Store, Play Protect puede solicitar su análisis. Esto no debe confundirse con una alerta de detección de software dañino. Si el sistema bloquea una compilación como potencialmente dañina, corresponde investigar la advertencia antes de distribuir o instalar esa compilación; no se recomienda desactivar Play Protect.

## Colaboradores en GitHub (1.0.28)

- **INTERNET**: solo se utiliza para leer `https://raw.githubusercontent.com/josejp2424/Essora-Launcher-android/main/supporters.json` al abrir el apartado Colaboradores o tocar **Actualizar**. Es una conexión HTTPS de solo lectura, con tiempo límite, validación de tamaño y caché local. No hay tareas de red periódicas.
- GitHub puede observar la IP y los datos técnicos de la conexión. La app no transmite información personal de la instalación, identificadores de pago, lista de apps ni preferencias.
- Los únicos nombres e importes publicados deben corresponder a personas que autorizaron expresamente su publicación. Se exige `public_consent: true` por entrada. El mantenedor debe conservar prueba de esa autorización y atender las solicitudes de retirada.
- Las donaciones son opcionales y no otorgan funciones digitales, contenido exclusivo, medallas ni recompensas. Los aportes se gestionan fuera de la aplicación.
- La lista pública puede modificarse en GitHub sin actualizar la APK. La app no escribe en GitHub ni necesita credenciales.

La política actualizada se encuentra en `docs/privacy-policy.html`, y debe publicarse mediante una URL pública estable antes de presentar la app a Google Play.
