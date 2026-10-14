# Privacy policy

*Last updated: 13 October 2026*

Tellycine is an IPTV player for Android phones, tablets and TVs. This policy explains what happens to your information when you use it. In short: Tellycine has no accounts, ads, analytics or tracking, and it doesn't send your information to its developer.

## What Tellycine stores, and where
Tellycine keeps these **only on your device**, in its private storage:

- the playlists you add, including their addresses and login details (for Xtream Codes-compatible servers, a username and password; for Dispatcharr, an API key and never your web password)
- TV guide data, favourites, watch history, resume positions and settings
- if you turn on details from TMDB: what TMDB said about your movies and series (for six months at most), and your own TMDB API key if you add one
- movies and episodes you download for offline viewing

They're removed when you delete the playlist or uninstall Tellycine. You can also clear history and downloads in the app.

**Device backups:** if backup is turned on in your device's settings, Android may include Tellycine's data (but not downloads) in your device backup, stored in your Google account. This is a feature of Android that you control in your device's settings.

## Who Tellycine connects to
- **The servers you add:** your playlist, TV guide, Xtream Codes-compatible and Dispatcharr servers, plus the artwork addresses they list. Tellycine sends them what they need to work, such as your login details. What those servers do with your information is up to whoever runs them; see their privacy policies.
- **Your phone, on your home network:** when you add a playlist to a TV by scanning a QR code, the TV briefly accepts the details from your phone over your local network. Nothing passes through the internet or any other server.
- **Google Cast:** to find Cast devices and play to them, Tellycine uses Google's Cast software. It sends Google anonymised information about Cast device discovery, Cast sessions, the device and the app, which Google uses to improve casting. See [Google's Cast data disclosure](https://developers.google.com/cast/docs/android_sender/data_disclosure) and [Google's privacy policy](https://policies.google.com/privacy).
- **TMDB, only if you turn it on:** with **Details from TMDB** on in Settings (it's off unless you turn it on), Tellycine sends TMDB (The Movie Database, api.themoviedb.org) the names and years of the movies and series in your playlists, or the TMDB ids your provider gives them, to find their details. It asks with Tellycine's own TMDB API key, or with yours if you add one. It sends nothing else: no logins, playlist addresses or watch history. Posters, backdrops and cast photos then load from TMDB's image server (image.tmdb.org). TMDB receives your device's IP address with each request, as any website does. If you add a key of your own, TMDB can link the requests made with it to your TMDB account; your key is stored on your device with your other settings, and only ever sent to TMDB. See [TMDB's privacy policy](https://www.themoviedb.org/privacy-policy). Turning it off stops it, and removing your key in Settings deletes it from your device.
- **Voice search on TV:** speech is handled by your TV's own speech recogniser, not by Tellycine.
- **Other apps, when you ask:** **Open with…** passes the stream address to the player you choose, and **Share stream URL** passes it to the app you share to. Stream addresses can include your provider login details, so only share them with people you trust. **Trailer** opens the trailer's YouTube address in the YouTube app (or your browser), which is covered by [Google's privacy policy](https://policies.google.com/privacy).

## What Tellycine doesn't do
- It has no developer servers, and the developer never receives your playlists, logins, history or anything else from the app.
- It has no accounts, advertising, analytics, crash reporting or tracking.
- It doesn't sell your information, or share it beyond what's described above.

## Children
Tellycine isn't directed at children, and its developer doesn't collect information from anyone, children included.

## Changes
If this policy changes, the new version will be published here with a new date.

## Contact
Questions about privacy: open an issue at [github.com/harborsyntax/tellycine/issues](https://github.com/harborsyntax/tellycine/issues), or email the developer address shown on Tellycine's Google Play page.
