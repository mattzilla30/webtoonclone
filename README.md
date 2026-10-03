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

## Added in the last round

- Chapter downloads with a Downloads screen, Wi-Fi-only saving, and offline reading.
- Collections with your own names, a title filter and unread filter in My Series, reading history with dates, and reading stats.
- Author follow with notifications for new series, grouped or combined chapter notifications, and a Mark read button.
- A daily backup to a folder you choose.
- Saved searches, blocked tags and scanlation groups, hidden series, a "Because you read" row, related series, a cover gallery, and a rating breakdown.
- Reader: keep-screen-on setting, page gap, next-chapter loading choices, and quiet retries for failed pages.
- A two-pane series page on wide screens, 256 px covers for small tiles, and a one-week cache for the tag list.

## Added during the polish loop

- My Series: three sort modes (recent, A-Z, unread first), counts on list chips, a title filter, "Mark all read", animated rows, and empty states with a next step. The tab carries a badge with the number of subscribed series that have unread chapters.
- Series page: status, year, demographic and language under the author, "N new" on the Continue button, and shares that include the title.
- Reader: a separate dimming and background per series, a screen direction lock, the series title in the top bar, and an option to save the next chapter while you read.
- Search: removable chips for active filters, Completed and other browse shortcuts, and a helpful empty state.
- Downloads: a confirmation before Remove all. The series info dialog can copy the title or the MangaDex link.
- Home and Updates: pull to refresh, a shuffle button on the hero, a bell on subscribed series in Updates, and a "Subscribed only" filter.
- Settings: a daily reading goal, clear reading history, reset reader options, share your library as text, a link to Android's notification settings, and the app version.
- An open-book app icon, a Downloads launcher shortcut, and haptic feedback on chips, toggles, page jumps, episode buttons and double-tap zoom.

## Settings

Settings is the last tab in the bottom bar:

- Theme: dark (the default), true black, light, or follow the system. Material You colors are optional.
- Data saver, reader background, volume-key scrolling, and reporting page loads to MangaDex.
- English or original (romanized) titles.
- Notifications on or off, and quiet hours.
- Cache size with a Clear cache button.

## Code style

```
./gradlew :app:ktlintFormat :app:ktlintCheck   # fix, then report what is left
```

The rules are in `.editorconfig`. ktlint does not remove unused imports, so check for them when moving code.

## Live API test

```
LIVE=true ./gradlew :app:testDebugUnitTest
```

Calls every MangaDex endpoint the app uses. The default build skips it.

## How it works

- Every ViewModel that loads over the network keeps its job and cancels the previous one before starting another, so a slow old request cannot overwrite newer results. `MangaDexHttp` cancels the HTTP call when its coroutine is cancelled.
- The library lives in Room. `LibraryStore.data` shares one set of queries between all screens. Use `LibraryStore.current()` for a read that must see the latest writes.
- JSON from the API and from saved copies is decoded on `Dispatchers.Default`, not the main thread.
- Settings are one JSON value in DataStore, decoded once per change.
- `SettingsStore.latest` and `LibraryStore.latest` hold the last values the app saw. Screens start from them, and `LibraryStore.stateOf` builds the library-backed StateFlows, so nothing flashes a default or empty state. The first frame waits up to a second for the saved settings, so the theme is right from the start.
- Wrap suspend network and storage calls in view models with `catching`, not `runCatching`, so a cancelled load stays cancelled.

## Layout

- `data/` MangaDex client, DTOs, and on-device storage (progress, recent, subscriptions, searches)
- `ui/home`, `ui/search`, `ui/series`, `ui/reader`, `ui/library` one screen and ViewModel each
- `MainActivity.kt` navigation and bottom bar

Some series only link to their publisher. Those episodes open in the browser.

## License

Dexter, the phone app and the Wear OS app, is free software: you can redistribute it and modify
it under the terms of the GNU General Public License, version 3, as published by the Free Software
Foundation. See [LICENSE](LICENSE).

Dexter comes with ABSOLUTELY NO WARRANTY. Every library it ships is open source as well: Apache 2.0
for AndroidX, Kotlin, Coil, OkHttp, Koin, Tesseract and its language data, BSD for Leptonica and
Protocol Buffers, and the libjpeg and libpng licenses for the image decoders inside Tesseract.
Settings → Open-source licenses lists them in the app.
