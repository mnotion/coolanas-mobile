# Coolanas Android

A native Android companion for Coolanas, built with Kotlin and Jetpack Compose. The mobile app uses the same Supabase account and backend as the Coolanas website.

## Features

- Google sign-in using Supabase Auth PKCE.
- Account-owned stopwatch controlled by the shared `coolanas_account_stopwatch_action` database RPC. The server is the source of truth; the app does not maintain a separate timer.
- Supabase Realtime updates for the signed-in account's stopwatch, with a periodic RPC refresh fallback.
- Subject selection and account-synced subject colours.
- Completed study-time analytics sent to the existing `coolanas-files-b2` `analytics_sync` action, using the database's `previous_started_at` and `server_now` values for session boundaries.
- Offline queue and retry for completed analytics records.
- Native Monthly and Calendar analytics, including daily totals, yearly activity, subject breakdowns, hourly activity, historical task snapshots, and a temporary preview of the current running interval.
- Account-synced appearance and profile avatar.

The stopwatch terminal and analytics are separate systems. The terminal stores the current account stopwatch state. Analytics store completed intervals for reporting.

## Technology

- Kotlin 2.4.21
- Jetpack Compose with Material 3
- Android Gradle Plugin 9.4.1
- Gradle wrapper 9.6.0
- Supabase Kotlin SDK 3.9.0
- Ktor 3.6.0 with the OkHttp engine (required for Supabase Realtime WebSockets)
- Minimum Android version: Android 8.0 (API 26)
- Compile SDK: API 37
- Target SDK: API 36
- Java/Kotlin bytecode target: 17

## Requirements

- A stable Android Studio release.
- A JDK supported by Android Gradle Plugin 9.4. The installed Android Studio includes JBR 25; Gradle is configured to use JDK 25. In Android Studio, confirm **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** points to the bundled runtime.
- Android SDK Platform 37, Android SDK Build-Tools, Platform-Tools, Command-line Tools, and Android Emulator. Install them in **Tools → SDK Manager**.
- For emulator use, create a virtual device in **Tools → Device Manager** using a stable Android system image. Hardware acceleration depends on Windows virtualization being enabled.

## Open and run

1. Open Android Studio and choose **Open**.
2. Select this existing `coolanas` project folder.
3. If Android Studio asks for an SDK location, choose the SDK folder shown in **Tools → SDK Manager**. This local path is stored in the ignored `local.properties` file and is not shared with other computers.
4. Wait for Gradle Sync to finish.
5. Select an emulator or an authorised Android phone and press **Run** beside the `app` configuration.

## Build and test

Run these commands from the project folder in PowerShell:

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat test
./gradlew.bat :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The last command needs a running emulator or an authorised Android phone.

To build a distributable APK, use **Build → Generate Signed Bundle / APK → APK** in Android Studio and select the `release` build variant. Keep the signing keystore and its passwords secure; future updates must use the same signing key.

## Project structure

```text
app/src/main/java/com/coolanas/app/
  MainActivity.kt             Authentication and app entry point
  StudyDashboardScreen.kt     Stopwatch controls, subjects, profile menu, Realtime
  StudyAnalyticsScreen.kt     Native Monthly and Calendar analytics
  StudySyncManager.kt         Stopwatch RPC, analytics queue, cloud sync, local cache
  SupabaseClientProvider.kt   Shared Supabase Auth, Functions, PostgREST, Realtime
  ui/theme/Theme.kt           Material 3 light/dark theme
app/src/main/res/             Android manifest, app label, theme, and launcher assets
app/src/androidTest/          Emulator/device Compose launch smoke test
gradle/libs.versions.toml     Central dependency and plugin versions
```

Keep device-specific SDK paths, signing files, secrets, and build output out of version control.
