# KUROMH - Mod Controller
Controller application for Kuro Mirror High Zygisk Module v0.4.

## Features
* Root detection & integration via `su` (Magisk / KernelSU)
* Reads and writes `kuro_mirror_high.cfg` dynamically
* Install the bundled Kuro Mirror High module from inside the app (Controller menu); "Upload Module" appears if the installer fails
* Module ON/OFF toggle (OFF stops the module immediately, no reboot)
* The module is removed automatically if this app is uninstalled (watchdog `service.sh` installed with the module)
* Config controls (Unity Speed, Damage Rate, God Mode, Dump, slider max) are kept in `ConfigControls.kt` and temporarily hidden
* Minimalist, dark-themed, ad-free UI using Jetpack Compose

## Project Structure
```
app/src/main
├── AndroidManifest.xml
├── assets/              kuro_mirror_high.zip (module), uninstall_watchdog.sh
├── res/values/themes.xml
└── java/com/kuromh/controller
    ├── MainActivity.kt
    ├── core/            ModuleConfig, ConfigManager, ModuleManager, RootUtil, MainViewModel
    └── ui/
        ├── MainScreen.kt        (bottom nav + NavHost)
        ├── components/Cards.kt  (komponen UI bersama)
        ├── screens/             Dashboard, Controller, Settings
        └── theme/               Color, Theme
```

## How to Build (Android Studio)
1. Open this project in Android Studio (JDK 17).
2. Wait for Gradle Sync to finish.
3. Select `app` from the run configurations.
4. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Install the generated APK on your rooted device.

## How to Build (GitHub Actions)
Push ke `main`/`master` atau jalankan workflow manual. APK hasil ada di **Artifacts**.
Opsional: isi secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` agar tanda tangan APK konsisten.

## Note
Grant Root Access (Magisk/KernelSU) when asked, so the app can read/write the module
configuration in `/data/adb/modules/kuro_mirror_high/` or `/data/local/tmp`.
