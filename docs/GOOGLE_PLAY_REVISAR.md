# Antes de presentar Essora Launcher en Google Play

- La aplicación nueva debe publicarse como **Android App Bundle (.aab)** firmado y se debe usar **Play App Signing**. El APK sigue siendo el formato de instalación directa en GitHub; el AAB no se instala directamente en el móvil.
- El source ya admite `./build.sh` para APK, `./build.sh --aab` para AAB y `./build.sh --both` para ambos. También existe `./scripts/compilar-aab.sh`. Se utiliza la firma preexistente fuera del repositorio, sin crear otra clave.
- **Atención a la firma entre GitHub y Google Play:** un AAB firmado con la clave actual NO implica que la APK entregada por Google Play use ese mismo certificado. Para que las APK instaladas desde GitHub puedan actualizarse desde Play sin desinstalar, durante el alta de Play App Signing elegí aportar **tu clave de firma de la aplicación ya existente** en lugar de permitir que Google genere una nueva; realizá el procedimiento oficial de importación con PEPK si Play Console te lo solicita. Podés utilizar una clave de carga diferente posteriormente.
- Conservá a salvo la `.jks` y las contraseñas. Nunca las subas a GitHub ni las introduzcas en archivos `build.gradle`, `gradle.properties` o logs.
- Publicá la política de privacidad en una URL HTTPS pública (por ejemplo, GitHub Pages sirviendo `docs/privacy-policy.html`) y usá esa URL en Play Console. Revisá los formularios de Seguridad de datos, permisos y declaración de cámara/linterna.
- El botón de PayPal es un aporte voluntario entre persona y creador. No se cobra por funciones y no se conceden beneficios digitales, insignias o premios; Google Play puede revisar el caso y sus políticas pueden cambiar.
- Publicá solo apodos e importes con consentimiento expreso. El historial de commits en GitHub es público: no incluyas datos bancarios, transacciones ni nombres reales sin autorización.
- Probá en dispositivo real el rol HOME, APK firmada, linterna, lector de icon packs, importar/compartir temas y el ranking offline/online.
- Asegurate de completar los requisitos de pruebas y verificaciones de tu tipo de cuenta en Play Console; la compilación no garantiza la aprobación.

Documentación oficial:
https://developer.android.com/studio/publish/app-signing
https://developer.android.com/build/building-cmdline
https://support.google.com/googleplay/android-developer/answer/10281818
https://support.google.com/googleplay/android-developer/answer/10144311
