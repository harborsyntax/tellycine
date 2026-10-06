<p align="center">
  <img src="branding/mosaic-icon-rounded.svg" width="112" alt="Mosaic app icon">
</p>

<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/mosaic-logo-dark.svg">
    <img src="branding/mosaic-logo-light.svg" width="300" alt="Mosaic">
  </picture>
</h1>

<p align="center">
  An IPTV player for Android phones and tablets, built with Jetpack Compose and Material 3.
  <br><br>
  <a href="https://github.com/mrjack711/tube/releases/latest"><b>Download the latest APK</b></a>
</p>

Mosaic is a player only. It does not provide any channels or content; you add your own
playlists from your provider.

## Features

- **Sources**: M3U/M3U8 links, local M3U files, and Xtream Codes logins.
  Paste an Xtream `get.php` link and Mosaic offers to switch to the Xtream API automatically.
- **TV guide**: XMLTV guides (plain or `.gz`), taken from the playlist's `url-tvg` header,
  from the Xtream provider, or from a URL you enter. Channels show what's on now with a
  progress bar, and long-pressing a channel opens its schedule.
- **Catch-up TV**: replay past programmes, or restart the current one, on channels with an
  archive. Works with Xtream Codes timeshift (honouring the server's timezone) and M3U
  `catchup`/`catchup-source` attributes (`default`, `append`, `shift`, `flussonic`).
- **Dispatcharr aware**: Dispatcharr's Xtream API is detected automatically. Catch-up uses its
  UTC timeshift, its placeholder expiry date is hidden, and pasting a Dispatcharr
  `/output/m3u` link suggests switching to its Xtream login.
- **Dispatcharr recordings**: connect your Dispatcharr login per playlist to browse, play,
  schedule (from any channel's guide) and delete DVR recordings. Mosaic stores only the
  account's API key, reusing the existing one rather than regenerating it.
- **Share & external players**: long-press a channel to open it in another app (VLC, MX
  Player, …) or share its stream URL; for Dispatcharr you can share either the Dispatcharr
  URL or the upstream source URL. A *Default player* setting makes an external app the
  default for everything.
- **Live TV, movies and series** in separate tabs, browsed by category, plus favourites,
  "continue watching" and recently watched channels.
- **Multiple playlists**: add as many as you like and switch between them from the title bar.
- **Per-playlist filtering**: tick or untick each playlist's categories (Live TV, Movies and
  Series separately) to hide them everywhere. You can also turn movies & series off for a
  playlist, which skips downloading them altogether.
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
  (1–7 days ahead, plus up to 7 days back for catch-up channels), are stored. Channels are matched on `tvg-id`, falling back to the
  display name.
- Lists are paged from Room with Paging 3, so scrolling stays smooth at any size.
- On launch, playlists older than 24 h and guides older than 12 h refresh in the background.

## Project layout

| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin/JVM parsers (M3U, XMLTV, Xtream JSON) and Xtream URL helpers, with unit tests |
| `app`  | Android app: Room database, sync engine, Compose UI, Media3 player |

## Branding

The launcher icon is the Mosaic tile grid (source: `mosaic-icon.svg`; app version
`res/drawable/ic_launcher_foreground.xml`, with a
single-colour `ic_launcher_monochrome.xml` for Android 13+ themed icons). The in-app logo pairs
the grid with a "Mosaic" wordmark set in Figtree Bold (SIL Open Font License) and converted to
vector outlines (`mosaic_wordmark.xml`), so it renders identically on every device.
SVG exports for use outside the app (light and dark logo, rounded icon) live in `branding/`.

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
