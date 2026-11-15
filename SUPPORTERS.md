# Colaboradores de Essora Launcher

El archivo público `supporters.json` está en la **raíz del repositorio**, en la rama `main`.

Consulta: https://raw.githubusercontent.com/josejp2424/Essora-Launcher-android/main/supporters.json

La aplicación 1.0.28 lo lee cuando el usuario abre **Configuración → Colaborar con Essora**, sin tener que publicar una APK nueva por cada cambio. Guarda la última copia válida y puede funcionar sin conexión. Si todavía no has subido el archivo al repositorio, se verá una lista vacía.

## Cómo añadir a alguien

1. Verificá en PayPal que el aporte sea real y no esté reembolsado; no se consulta PayPal desde la app.
2. Pedí autorización expresa para publicar el apodo en el muro. Aunque la interfaz solo muestra nombres y notas, tené en cuenta que `supporters.json` es público y cualquier persona puede consultar los importes que incluyas allí. No publiques un importe sin el consentimiento correspondiente. No publiques información bancaria ni direcciones de correo. Conservá la prueba del consentimiento fuera del repositorio.
3. Editá `supporters.json` en GitHub y elegí **Commit changes**. El nuevo contenido aparecerá al abrir o actualizar la sección en Essora.
4. Si alguien pide retirar su nombre, quitá la entrada del JSON. Tené en cuenta que GitHub conserva el historial público de commits: evitá publicar datos personales innecesarios desde el principio.

Ejemplo ficticio (no publiqués estos nombres como aportes reales):

```json
{
  "updated": "2026-11-15",
  "supporters": [
    {
      "name": "Usuario de ejemplo",
      "amount": 10.00,
      "currency": "USD",
      "note": "",
      "public_consent": true
    },
    {
      "name": "Ejemplo en otra moneda",
      "amount": 4000.00,
      "currency": "ARS",
      "amount_usd": 3.00,
      "note": "",
      "public_consent": true
    }
  ]
}
```

**Organización de la lista:** las entradas deben contener los aportes acumulados por persona, no una entrada por transacción. La aplicación muestra un muro de agradecimiento con los nombres autorizados, sin números de puesto ni importes. Si un aporte está en otra moneda, `amount_usd` debe ser su equivalente en dólares verificado por vos, con un criterio de conversión coherente. Si falta, la entrada de moneda distinta a USD no se muestra para evitar comparaciones equivocadas. Los importes negativos, nulos y los registros sin `public_consent: true` tampoco se muestran.

**Privacidad:** no ingreses datos de donantes que no hayan autorizado expresamente aparecer. Usá apodos autorizados o omití la entrada para colaboradores anónimos. Para retirar una publicación pasada, también evaluá el historial público de GitHub.

**Google Play:** no se ofrecen ventajas, compras, anuncios, insignias ni contenido exclusivo por donar. El muro es un agradecimiento público voluntario, no un beneficio dentro de la aplicación. Google puede revisar igualmente su tratamiento según la política de pagos vigente.
