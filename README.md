# Webtoon Clone

A native Android reader for MangaDex. Kotlin, Jetpack Compose, dark theme.
Targets Android API 37 on 64-bit ARM (`arm64-v8a`) only.

## Build

Requires JDK 21 and the Android SDK with platform `android-37.0`.

```
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. An emulator on an x86_64 host needs an
arm64 system image, because the APK ships no x86 libraries.

## Signed release

Add these to `~/.gradle/gradle.properties`, then run `./gradlew :app:assembleRelease`:

```
RELEASE_STORE_FILE=/path/to/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Without them the release APK is unsigned.

## Live API test

```
LIVE=true ./gradlew :app:testDebugUnitTest
```

Calls every MangaDex endpoint the app uses. The default build skips it.

## Layout

- `data/` MangaDex client, DTOs, and on-device storage (progress, recent, subscriptions, searches)
- `ui/home`, `ui/search`, `ui/series`, `ui/reader`, `ui/library` one screen and ViewModel each
- `MainActivity.kt` navigation and bottom bar

Some series only link to their publisher. Those episodes open in the browser.
