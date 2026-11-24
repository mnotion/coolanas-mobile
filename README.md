# Coolanas Android

A native Android starter project for the Coolanas app. It uses Kotlin and Jetpack Compose and intentionally opens to a blank screen. No product features or backend connections are included yet.

## Technology

- Kotlin 2.4.21
- Jetpack Compose with Material 3 (stable releases compatible with API 36)
- Android Gradle Plugin 9.4.1
- Gradle wrapper 9.6.0
- Minimum Android version: Android 8.0 (API 26)
- Compile and target SDK: API 36 (latest stable Android platform)
- Java/Kotlin bytecode target: 17

## Requirements

- A stable Android Studio release.
- A JDK supported by Android Gradle Plugin 9.4. The installed Android Studio includes JBR 25; Gradle is configured to use JDK 25. In Android Studio, confirm **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** points to the bundled runtime.
- Android SDK Platform 36, Android SDK Build-Tools, Platform-Tools, Command-line Tools, and Android Emulator. Install them in **Tools → SDK Manager**.
- For emulator use, create a virtual device in **Tools → Device Manager** using a stable Android system image. Hardware acceleration depends on Windows virtualization being enabled.

## Open and run

1. Open Android Studio and choose **Open**.
2. Select this existing `coolanas` project folder.
3. If Android Studio asks for an SDK location, choose the SDK folder shown in **Tools → SDK Manager**. This local path is stored in the ignored `local.properties` file and is not shared with other computers.
4. Wait for Gradle Sync to finish.
5. Select an emulator in the device selector and press **Run** beside the `app` configuration. A connected phone with USB debugging enabled can also be selected.

## Build and test

Run these commands from the project folder in PowerShell:

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat test
./gradlew.bat :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The last command needs a running emulator or an authorised Android phone.

To use a phone, enable **Developer options** and **USB debugging** on the phone, connect it by USB, accept the authorisation prompt, then check `adb devices` before installing or running from Android Studio.

## Project structure

```text
app/src/main/java/com/coolanas/app/
  MainActivity.kt        Compose launcher activity and blank app surface
  ui/theme/Theme.kt      Central Material 3 theme
app/src/main/res/        Android manifest, app label, theme, and launcher assets
app/src/androidTest/     Emulator/device Compose launch smoke test
gradle/libs.versions.toml Central dependency and plugin versions
```

Future screens, navigation, data, domain logic, and shared UI components can be added as they are needed. Keep device-specific SDK paths, credentials, signing files, and build output out of version control.
