# Development

## Project layout
| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin/JVM: streaming M3U, XMLTV and Xtream JSON parsers, catch-up URL builders, Dispatcharr API parsing. Unit-tested. |
| `app` | Android app: Room database, sync engine, Jetpack Compose UI (Material 3) for phones and tablets, the TV interface (`app.tellycine.tv`), Media3 player, Cast, DVR. |

## Key libraries
Jetpack Compose with Material 3 (including the adaptive navigation suite), Compose for TV (`androidx.tv:tv-material`), Room with Paging 3, Media3 (ExoPlayer, HLS, DASH, Cast), Coil 3, OkHttp, DataStore, and Gson's streaming reader.

## Architecture
- **Sync:** playlists, Xtream responses and XMLTV guides are downloaded to a temporary file, stream-parsed, and imported in a single SQLite transaction (WAL mode). The UI keeps reading the old data until the import commits.
- **UI:** a single activity with Compose Navigation. `NavigationSuiteScaffold` gives a bottom bar on phones and a navigation rail on tablets. Lists are paged from Room.
- **TV:** one APK serves phones and TVs. The manifest offers TV support without requiring it (`android.software.leanback` and touchscreen are optional) and adds `TvActivity` to the TV launcher with a banner; `MainActivity` hands over to it on a TV. The TV screens live in `app.tellycine.tv` and reuse the phone's ViewModels and data layer; `PlayerActivity` swaps in `TvPlayerScreen`, driven by the remote. TV Material acts on the release of OK and fires its long press while OK is held, so TV items use `okClicks` (click and long press, both on release) and dialogs ignore stray OK releases.
- **Add from phone:** `PhoneSetupServer` is a small HTTP server that only runs while the TV's Add from phone screen is open. It serves a form to the local network and needs the random token in the address shown on the TV.
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

TV screenshots (`TvScreenshots`, `TvPlayerScreenshots`) drive the TV interface with remote-control key presses at 960×540 dp, the size of a 1080p TV. `TvNavigationTest` checks the remote's navigation.

The **Screenshots** workflow does the same on GitHub Actions (run it from the Actions tab) and commits the images back to the branch.

## CI and releases
- **Android CI:** runs on every push and PR. It runs the core tests and builds debug and release APKs.
- **Release:**
  - **When it runs:** on pushes to `main`. If `versionName` in `app/build.gradle.kts` hasn't been released yet, it builds, verifies the signature and publishes `v<versionName>` with the APK and a SHA-256 file.
  - **Release notes:** highlights come from `release-notes/v<version>.md`.
  - **To release:** bump `versionName` and `versionCode` in a PR, add the release notes, and merge.

## Signing
- **Secrets:** releases are signed with the permanent Tellycine key from the repository secrets `TELLYCINE_KEYSTORE_BASE64`, `TELLYCINE_KEYSTORE_PASSWORD`, `TELLYCINE_KEY_ALIAS` and `TELLYCINE_KEY_PASSWORD`.
- **Safety check:** the Release workflow refuses to publish if the secrets are missing or the APK's certificate isn't the expected one.
- **Keep the keystore backed up.** Losing it means future versions can't update existing installs.

## Database migrations
Room migrations live in `TellycineDatabase`. Add a `Migration` for every schema change, so updates keep users' playlists and history.
