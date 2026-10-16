<p align="center">
  <img src="branding/tellycine-icon-rounded.svg" width="112" alt="Tellycine app icon">
</p>

<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/tellycine-logo-dark.svg">
    <img src="branding/tellycine-logo-light.svg" width="300" alt="Tellycine">
  </picture>
</h1>

<p align="center">
  <img src="branding/play-store-coming-soon.svg" width="200" alt="Coming soon to Google Play">
</p>

<p align="center">
  An IPTV player for Android phones, tablets and TVs, built with a Material 3 UI.
  <br><br>
  <a href="https://github.com/harborsyntax/tellycine/releases/latest"><b>Download the latest APK</b></a>
  &nbsp;·&nbsp;
  <a href="docs/Home.md"><b>Documentation</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/harborsyntax/tellycine/issues"><b>Report a bug or suggest a feature</b></a>
</p>

<p align="center">
  <img src="docs/images/screens/live-tv.png" width="200" alt="Live TV">
  <img src="docs/images/screens/guide-sheet.png" width="200" alt="TV guide">
  <img src="docs/images/screens/movies.png" width="200" alt="Movies">
  <img src="docs/images/screens/library.png" width="200" alt="Library">
</p>

Tellycine is a player only. It doesn't provide any channels or content; you add playlists from your own provider.

## Features

- **Your sources:** M3U/M3U8 links, local M3U files, Xtream Codes accounts and Dispatcharr servers, with as many playlists as you like, browsed one at a time or all together.
- **Live TV with a TV guide:** XMLTV guides, "on now" with progress, and a full schedule for each channel.
- **Catch-up TV:** replay past programmes, or restart the current one. Works with Xtream timeshift and M3U `catchup` attributes.
- **Movies & series:** poster grids sorted your way (date added, release date, latest episode, title), details with full backdrops and trailers, seasons and episodes, and resume where you left off. Optionally, details from TMDB: cast and crew, ratings, missing posters, episode details, collections and titles like the one you're looking at, and sorting by rating or when series last aired.
- **Search:** find channels, programmes in the TV guide, movies and series across all your playlists in one place.
- **Downloads:** save movies and episodes to watch offline, with a screen to manage them and a Wi-Fi only option.
- **Library:** favourites, continue watching and recently watched, with sorting and grouping by category.
- **Your choice of content:** hide categories per playlist, or turn off movies & series entirely.
- **A capable player:** HLS, MPEG-TS, DASH and MP4, Dolby Digital, DTS and MP2 sound even on phones that can't play them, audio and subtitle tracks, channel switching, and picture-in-picture. On phones, it plays at the top of the app while you browse, and goes full screen when you turn it sideways.
- **Chromecast**, plus **Open with…** any external player, or set one as your default.
- **Dispatcharr:** DVR recordings (watch, schedule and delete), and sharing Dispatcharr or source stream URLs.
- **Built for big playlists:** stream-parsed imports and paged lists keep huge playlists and guides fast.
- **Material 3 design:** Material You colours, light and dark themes, and layouts that adapt to phones, tablets and foldables.
- **Android TV & Google TV:** a full TV interface for the remote, with a traditional grid guide, channel switching and number entry, multi-view (up to four channels at once), voice search, and adding playlists from your phone with a QR code. Same app, same APK.

<p align="center">
  <img src="docs/images/screens/tablet-live-tv.png" width="640" alt="Tablet layout">
</p>

<p align="center">
  <img src="docs/images/screens/tv-guide.png" width="640" alt="Android TV guide">
</p>

## Bugs and ideas

Found a bug, or have an idea for a feature? Please [open an issue on GitHub](https://github.com/harborsyntax/tellycine/issues/new). Before you do, check whether [someone has already raised it](https://github.com/harborsyntax/tellycine/issues): if so, add a 👍 or your details there instead.

For a bug, it helps to include:

- what you did, what happened, and what you expected to happen;
- your device (phone, tablet or TV, and its model) and Android version;
- the app version, from **Settings** (shown at the bottom);
- your playlist type (M3U link or file, Xtream Codes or Dispatcharr). Please don't post your playlist link, login or any stream addresses.

## Documentation

See the **[documentation](docs/Home.md)** for installation, adding playlists, catch-up, Chromecast, Dispatcharr, [Android TV](docs/Android-TV.md), troubleshooting and developer notes.

## Building

```bash
./gradlew :core:test :app:assembleDebug
```

Requires JDK 17 and the Android SDK. See [Development](docs/Development.md) for architecture, releases and signing.

## Credits

Movie and series details, when you turn them on, come from [TMDB](https://www.themoviedb.org). This product uses the TMDB API but is not endorsed or certified by TMDB.

Dolby Digital, DTS and MPEG sound is played with [FFmpeg](https://ffmpeg.org), licensed under the [GNU LGPL 2.1](https://github.com/FFmpeg/FFmpeg/blob/n6.0.1/COPYING.LGPLv2.1) or later, through Media3's FFmpeg extension (Apache License 2.0). See [`ffmpeg`](ffmpeg/README.md) for its source and how it's built.
