# KUROMH - Mod Controller
Controller application for Kuro Mirror High Zygisk Module v0.4.

## Features
* Root Detection & Integration via `su`
* Reads and writes `kuro_mirror_high.cfg` dynamically
* Adjust Unity Speed, Damage Rate, God Mode, and Diagnostic Dump real-time.
* Minimalist, dark-themed, and ad-free UI using Jetpack Compose.

## How to Build (Android Studio)
1. Open this project in Android Studio.
2. Wait for Gradle Sync to finish.
3. Select `app` from the run configurations.
4. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Install the generated APK on your rooted device.

## Note
Ensure you grant Root Access (Magisk/KernelSU) when the app launches so it can read/write the module configuration in `/data/adb/modules/kuro_mirror_high/` or `/data/local/tmp`.
