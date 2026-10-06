# Tube

An IPTV player for Android phones and tablets, built with Jetpack Compose and Material 3.

Tube is a player only. It does not provide any channels or content; you add your own
playlists from your provider.

## Features

- **Sources**: M3U/M3U8 links, local M3U files, and Xtream Codes logins.
  Paste an Xtream `get.php` link and Tube offers to switch to the Xtream API automatically.
- **TV guide**: XMLTV guides (plain or `.gz`), taken from the playlist's `url-tvg` header,
  from the Xtream provider, or from a URL you enter. Channels show what's on now with a
  progress bar, and long-pressing a channel opens its schedule.
- **Live TV, movies and series** in separate tabs, browsed by category, plus favourites,
  "continue watching" and recently watched channels.
- **Multiple playlists**: add as many as you like and switch between them from the title bar.
- **Search** across channels, movies and series.
- **Player** built on Media3/ExoPlayer: HLS, MPEG-TS, DASH and progressive files, audio
  and subtitle track selection, resume, channel up/down and an in-player channel list,
  picture-in-picture, and aspect-ratio modes. You can set a per-playlist user agent, and
  per-channel `http-user-agent`/`Referer` hints from the playlist are honoured.
- **Adaptive layout**: a bottom navigation bar on phones, and a navigation rail with
  list/detail panes on tablets and foldables. Supports Material You dynamic colour and
  light/dark themes.

## Handling large playlists

- Playlists, Xtream responses and EPGs are **stream-parsed**, never loaded into memory
  whole, so 100k+ entry playlists and multi-hundred-MB guides work.
- Each source is downloaded to a temp file and imported in **one SQLite transaction**
  (WAL mode). The UI keeps showing the old data until the import commits, and a failed
  refresh never leaves a half-empty playlist.
- Only guide entries for channels in your playlist, within the configured window
  (1–7 days), are stored. Channels are matched on `tvg-id`, falling back to the
  display name.
- Lists are paged from Room with Paging 3, so scrolling stays smooth at any size.
- On launch, playlists older than 24 h and guides older than 12 h refresh in the background.

## Project layout

| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin/JVM parsers (M3U, XMLTV, Xtream JSON) and Xtream URL helpers, with unit tests |
| `app`  | Android app: Room database, sync engine, Compose UI, Media3 player |

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew :core:test            # parser unit tests (JVM only)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease  # minified, signed with the debug key for side-loading
```

GitHub Actions builds both APKs on every push and uploads them as workflow artifacts.
Before publishing anywhere, replace the debug signing config in `app/build.gradle.kts`
with your own keystore.
