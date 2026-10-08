# Development

## Project layout
| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin/JVM: streaming M3U, XMLTV and Xtream JSON parsers, catch-up URL builders, Dispatcharr API parsing. Unit-tested. |
| `app` | Android app: Room database, sync engine, Jetpack Compose UI (Material 3), Media3 player, Cast, DVR. |

## Key libraries
Jetpack Compose with Material 3 (including the adaptive navigation suite), Room with Paging 3, Media3 (ExoPlayer, HLS, DASH, Cast), Coil 3, OkHttp, DataStore, and Gson's streaming reader.

## Architecture
- **Sync:** playlists, Xtream responses and XMLTV guides are downloaded to a temporary file, stream-parsed, and imported in a single SQLite transaction (WAL mode). The UI keeps reading the old data until the import commits.
- **UI:** a single activity with Compose Navigation. `NavigationSuiteScaffold` gives a bottom bar on phones and a navigation rail on tablets. Lists are paged from Room.
- **Settings:** stored in DataStore. Hidden categories are kept in their own table, so they survive refreshes.
- **Dependencies:** a small hand-written `AppContainer`, with no DI framework.

## Building
Requirements: JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew :core:test            # parser and helper unit tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease  # minified; debug-signed unless TELLYCINE_KEYSTORE is set
```

## Screenshots
The documentation screenshots come from the real UI, rendered with Robolectric and Roborazzi against a fictional demo playlist (`app/src/test/.../screenshots`):

```bash
./gradlew :app:testDebugUnitTest --tests '*Screenshots*'   # writes docs/images/screens/*.png
```

The **Screenshots** workflow does the same on GitHub Actions (run it from the Actions tab) and commits the images back to the branch.

## CI and releases
- **Android CI:** runs on every push and PR. It runs the core tests and builds debug and release APKs.
- **Release:**
  - **When it runs:** on pushes to `main`. If `versionName` in `app/build.gradle.kts` hasn't been released yet, it builds, verifies the signature and publishes `v<versionName>` with the APK and a SHA-256 file.
  - **Release notes:** highlights come from `release-notes/v<version>.md`.
  - **To release:** bump `versionName` and `versionCode` in a PR, add the release notes, and merge.

## Signing
- **Secrets:** releases are signed with the permanent Tellycine key from the repository secrets `TELLYCINE_KEYSTORE_BASE64`, `TELLYCINE_KEYSTORE_PASSWORD`, `TELLYCINE_KEY_ALIAS` and `TELLYCINE_KEY_PASSWORD`. The older `MOSAIC_*` names, from before the app was renamed, still work as a fallback.
- **Safety check:** the Release workflow refuses to publish if the secrets are missing or the APK's certificate isn't the expected one.
- **Keep the keystore backed up.** Losing it means future versions can't update existing installs.

## Database migrations
Room migrations live in `TellycineDatabase`. Add a `Migration` for every schema change, so updates keep users' playlists and history.
