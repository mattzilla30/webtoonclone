# Dexter

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

## Install

This is a personal app. The release build is signed with the debug key, so `./gradlew :app:assembleRelease` gives an APK you can install directly.

## Features added in this round

- Reading lists (Reading, Plan to Read, Completed, Dropped) from a status button on the series page, and a LISTS tab in My Series.
- Backup and restore to a JSON file in Settings. The library database and preferences also join Android's cloud backup.
- mangadex.org title and chapter links open in the app. Launcher shortcuts (Search, My Series, Continue) and a Continue Reading widget.
- Advanced search: status, demographic, original language, year, and include or exclude tags with match all or any.
- Author pages, similar series, scanlation group credits with a preferred group per series, volume headings, and a content language setting.
- Paged reading modes, zoom, and tap zones in the reader.
- Side navigation rail and wider cover grids on tablets. Screen transitions.
- Koin for dependency injection, a hand-written baseline profile, and a CI workflow (style, lint, unit tests, release build).

## Settings

My Series has a gear icon that opens Settings:

- Theme: dark (the default), true black, light, or follow the system. Material You colors are optional.
- Data saver, reader background, volume-key scrolling, and reporting page loads to MangaDex.
- English or original (romanized) titles.
- Notifications on or off, and quiet hours.
- Cache size with a Clear cache button.

## Code style

```
./gradlew :app:ktlintCheck    # report problems
./gradlew :app:ktlintFormat   # fix them
```

The rules are in `.editorconfig`.

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
