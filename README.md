# Essora Launcher

![Essora Launcher — Coffee theme](screenshot/Lanzador%20Essora_%20Tema%20Caf%C3%A9.png)

**Essora Launcher** is a lightweight Android home screen written in native Java. It uses a minimal interface with rounded app shortcuts, an integrated clock and battery indicator, and configurable gestures and themes. The project is developed by **Essora Project** and released under the **GNU GPL-3.0** license.

**Freedom to Create. Freedom to Explore.**

## Features

- Customizable application bubbles and five-button bottom dock.
- Clock, date and battery indicator built into the home screen.
- Eleven built-in themes: **Essora Color, Black, Green, Pink, Yellow, Hex, Peaks, Sunset, Zen, Autumn and Coffee**.
- Optimized wallpapers and theme previews; **Essora Green** is selected by default for new installations. Existing choices are preserved on updates.
- Configurable gestures: swipe up opens the app drawer and **double-tap on an empty area toggles the flashlight** by default. Other gesture assignments are unchanged and can be customized. Camera permission is requested only when the flashlight is first used.
- **Essora Theme Creator**: create themes with an author, colors, wallpaper, credits and custom app icons. Save, import, export and share `.essoratheme` files using Android's native share menu.
- Manual theme installation through Android's document picker, including downloaded files.
- Support for compatible installed ADW/Nova icon packs, including Arcticons, plus individual custom icons and Essora ZIP icon packs.
- Eleven interface languages: English, Arabic, Catalan, German, Spanish, French, Italian, Portuguese, Hungarian, Russian and Japanese.
- An optional **Support Essora** entry in Settings opens [PayPal.Me](https://paypal.me/essoralinux) in a compatible browser/app. No payment SDK, mandatory contribution or extra permission is needed.
- No permanent background service and no external UI framework.

## Installation and Google Play Protect

Essora Launcher is **not currently available on Google Play**. When installing a downloaded APK, **Google Play Protect may ask to scan the application** because it has not previously verified that package or build. An app-scan request is not, by itself, a finding that the app is harmful. Follow Android's security prompts and review any warnings; **if Play Protect flags or blocks the APK as harmful, do not bypass the warning—report the details so the issue can be investigated**.

The application is still undergoing testing. We hope to submit it to Google Play after testing, but there is no confirmed approval or release date.

Google's documentation: [Play Protect warnings for developers](https://developers.google.com/android/play-protect/warning-dev-guidance).

## Building on Essora GNU/Linux

The project uses Android SDK API 36, Android Build Tools 35.0.0, Java 17, and Gradle 8.13. No Android Studio is required.

```bash
./scripts/instalar-sdk.sh  # only if the Android SDK is not installed
./build.sh
```

The signed release APK will be generated at:

`dist/Essora-Launcher-1.0.24-release.apk`

The build script requires a **release keystore stored outside this repository** at `$HOME/.local/share/essora-launcher/signing/essora-launcher-release.jks` (or under `$XDG_DATA_HOME`). If you already installed a previous build, **keep using the same keystore and password**; signing with a new key will prevent updating that installation. For a first-time independent build, `./scripts/crear-firma.sh` can create your own key. Never upload the keystore or signing credentials.

To build a separate test APK without release signing:

```bash
./build.sh --debug
```

The application ID for release builds is `org.essora.launcher` (`versionName 1.0.24`, `versionCode 25`). The minimum supported Android API is 26.

## Themes and icons

The theme creator exports a `.essoratheme` package containing its settings, author credits, compatible artwork and the required GPL-3.0 license. Sharing uses the Android share menu (e.g., Telegram or WhatsApp). Themes can also be installed manually from Downloads.

Compatible installed icon packs are referenced rather than embedded in shared theme archives; users must install the relevant pack separately. Only redistribute wallpaper or icon artwork when its original license permits it.

See [Theme and icon pack formats](FORMATOS-DE-TEMAS.md) and [Security and permissions](SEGURIDAD_Y_PERMISOS.md).

## License

Essora Launcher is free software under **GNU GPL-3.0**. See [LICENSE](LICENSE). Third-party icon packs and artwork keep their own licenses and attribution requirements.
