# Development

## Project layout
| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin/JVM: streaming M3U, XMLTV and Xtream JSON parsers, catch-up URL builders, Dispatcharr API parsing. Unit-tested. |
| `app` | Android app: Room database, sync engine, Jetpack Compose UI (Material 3) for phones and tablets, the TV interface (`app.tellycine.tv`), Media3 player, Cast, DVR. |
| `ffmpeg` | Media3's FFmpeg audio decoder, for Dolby, DTS and MPEG audio on devices with no decoder of their own: its Java classes, and `libffmpegJNI.so` built from FFmpeg's source. See [its README](../ffmpeg/README.md). |

## Key libraries
Jetpack Compose with Material 3 (including the adaptive navigation suite), Compose for TV (`androidx.tv:tv-material`), Room with Paging 3, Media3 (ExoPlayer, HLS, DASH, Cast), Coil 3, OkHttp, DataStore, and Gson's streaming reader.

## Architecture
- **Sync:** playlists, Xtream responses and XMLTV guides are downloaded to a temporary file, stream-parsed, and imported in a single SQLite transaction (WAL mode). The UI keeps reading the old data until the import commits.
- **UI:** a single activity with Compose Navigation. `NavigationSuiteScaffold` gives a bottom bar on phones and a navigation rail on tablets. Lists are paged from Room.
- **TV:** one APK serves phones and TVs. The manifest offers TV support without requiring it (`android.software.leanback` and touchscreen are optional) and adds `TvActivity` to the TV launcher with a banner; `MainActivity` hands over to it on a TV. The TV screens live in `app.tellycine.tv` and reuse the phone's ViewModels and data layer; `PlayerActivity` plays with `TvPlayerScreen`, driven by the remote. TV Material acts on the release of OK and fires its long press while OK is held, so TV items use `okClicks` (click and long press, both on release) and dialogs ignore stray OK releases.
- **Add from phone:** `PhoneSetupServer` is a small HTTP server that only runs while the TV's Add from phone screen is open. It serves a form to the local network and needs the random token in the address shown on the TV.
- **Sound:** `PlayerFactory` adds the `ffmpeg` module's renderer after the device's own decoders (`EXTENSION_RENDERER_MODE_ON`), so it's used only for sound the device can't play. When none of a stream's sound can be played, `unplayableAudio` names its format and the player says so.
- **Player:** `PlaybackEngine` holds what both players share (opening streams, retries, channel switching, Chromecast, resume points). On a TV it lives in `PlayerActivity`. On phones the app keeps it (`PhonePlayback`), so `MainActivity` shows it docked at the top in portrait, full screen in landscape and in picture-in-picture, through `PhonePlayerHost`, without the stream starting again.
- **Settings:** stored in DataStore. Hidden categories are kept in their own table, so they survive refreshes.
- **TV guides:** `GuideUrls` decides which XMLTV guides a playlist loads: the ones you added, the playlist's own (from the M3U header or the Xtream provider), or both. They're imported together, and `EpgMatcher` gives each channel one source and drops repeated programmes.
- **Kept across refreshes:** a refresh replaces a playlist's titles, so `FirstSeen` carries over what Tellycine worked out: when titles were first seen, series' change dates (from the provider, or M3U episode counts), and what TMDB said about each title.
- **Details from TMDB (opt-in):** with Tellycine's own TMDB key (`TmdbBuiltInKey`), or the user's if they add one. `TmdbUpdater` keeps the lists' TMDB ids, scores, posters and series air dates up to date: titles with the same cleaned-up name and year (`ShowNames`) share one lookup in `tmdb_titles`, a provider's TMDB id saves searching, and after the first look-up it asks only about what's due; see the class for the rules. `TmdbLookup` fetches a title's full page (credits, videos, images, age ratings, recommendations) when it's opened, kept in `tmdb_pages`. Nothing is kept longer than the six months TMDB's terms allow. `TmdbClient` spaces requests out and backs off when TMDB says it's busy; `core`'s `Tmdb` parses the answers.
- **Dependencies:** a small hand-written `AppContainer`, with no DI framework.

## Building
Requirements: JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew :core:test            # parser and helper unit tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease  # minified; debug-signed unless TELLYCINE_KEYSTORE is set
./gradlew :app:bundleRelease    # the app bundle (.aab) for Google Play, signed the same way
```

## Screenshots
The documentation screenshots come from the real UI, rendered with Robolectric and Roborazzi against a fictional demo playlist (`app/src/test/.../screenshots`):

```bash
./gradlew :app:testDebugUnitTest --tests '*Screenshots*'   # writes docs/images/screens/*.png
```

TV screenshots (`TvScreenshots`, `TvPlayerScreenshots`) drive the TV interface with remote-control key presses at 960×540 dp, the size of a 1080p TV. `TvNavigationTest` checks the remote's navigation.

The Google Play listing's screenshots and graphics come from the same UI at Play's sizes (`StoreListing.kt`), written to `fastlane/metadata/android/en-GB/images`:

```bash
./gradlew :app:testDebugUnitTest --tests '*Store*'
```

The **Screenshots** workflow does both on GitHub Actions (run it from the Actions tab) and commits the images back to the branch.

## FFmpeg decoders
`ffmpeg/src/main/jniLibs` holds `libffmpegJNI.so` for each ABI, built from a pinned FFmpeg release with only its LGPL parts and the audio decoders Tellycine needs. The **FFmpeg decoders** workflow builds them with `ffmpeg/natives/build.sh` when anything in `ffmpeg/natives` changes (or from the Actions tab) and commits them, with `ffmpeg/BUILT.txt`, back to the branch. To change the decoders or FFmpeg's version, edit `build.sh`. See [`ffmpeg/README.md`](../ffmpeg/README.md).

## CI and releases
- **Android CI:** runs on every push and PR. It runs the core tests and builds debug and release APKs.
- **Release:**
  - **When it runs:** on pushes to `main`. If `versionName` in `app/build.gradle.kts` hasn't been released yet, it builds, verifies the signatures and publishes `v<versionName>` with the APK, a SHA-256 file and the app bundle for Google Play.
  - **Release notes:** highlights come from `release-notes/v<version>.md`.
  - **Google Play:** a second job then uploads the app bundle to Google Play's closed testing track and sends it for review (see [Google Play](#google-play)).
  - **To release:** bump `versionName` and `versionCode` in a PR, add the release notes and the Play changelog, and merge.

## Google Play
- **What's new:** the Play changelog for each version is `fastlane/metadata/android/en-GB/changelogs/<versionCode>.txt`, up to 500 characters. The Release workflow checks it before publishing anything.
- **Upload:** the Release workflow's **google-play** job (`.github/scripts/play.py`) uploads the bundle to the track in `PLAY_TRACK` (`alpha`, closed testing), with that changelog, and sends it for review. A newer version replaces one still in review. Once Google approves it, it reaches testers.
- **If it fails:** the GitHub release is already out. Fix the cause, then use **Re-run failed jobs** on the run. It carries on where it stopped, and does nothing if the version is already on the track.
- **Access:** it signs in as a Google Cloud service account whose JSON key is the repository secret `PLAY_SERVICE_ACCOUNT_JSON`. In Play Console, under **Users and permissions**, that account has access to Tellycine with **Release to testing tracks**.
- **Checking the connection:** the **Google Play check** workflow (run it from the Actions tab) signs in, lists the tracks with their API names and changes nothing.

## Signing
- **Secrets:** releases are signed with the permanent Tellycine key from the repository secrets `TELLYCINE_KEYSTORE_BASE64`, `TELLYCINE_KEYSTORE_PASSWORD`, `TELLYCINE_KEY_ALIAS` and `TELLYCINE_KEY_PASSWORD`.
- **TMDB key:** Tellycine's own TMDB key comes from the repository secret `TMDB_API_KEY`, which the Release workflow and Android CI's APK builds pass in as an environment variable. The build scrambles it into `BuildConfig` so it isn't a readable string in the app (anyone who unpacks the app can still recover it, which is why people can use their own key instead). Without the secret, a build has no key of its own and asks people for theirs; the Release workflow warns when it's missing. Unit tests expect a build without it, so CI doesn't pass it to them.
- **Safety check:** the Release workflow refuses to publish if the secrets are missing or the APK's or app bundle's certificate isn't the expected one.
- **Keep the keystore backed up.** Losing it means future versions can't update existing installs.

## Database migrations
Room migrations live in `TellycineDatabase`. Add a `Migration` for every schema change, so updates keep users' playlists and history.
