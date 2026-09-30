# Releasing

1. Create a keystore once: `keytool -genkeypair -v -keystore release.jks -alias webtoonclone -keyalg RSA -keysize 4096 -validity 10000`. Keep it out of the repo.
2. Add four repository secrets: `RELEASE_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
3. Tag a version: `git tag v0.2.0 && git push origin v0.2.0`. The Release workflow builds the signed APK and the Play bundle, and attaches both to a GitHub release. The version name comes from the tag and the version code from the run number.
4. To build locally, put the four `RELEASE_*` values in `~/.gradle/gradle.properties` and run `./gradlew :app:assembleRelease :app:bundleRelease`. Add `-PVERSION_NAME=0.2.0` and `VERSION_CODE=12` in the environment to set the version.
5. Upload the bundle to Play Console. Use `store/listing.md` for the store text.

The app uses Android 17 (API 37) and ships 64-bit ARM libraries only.
