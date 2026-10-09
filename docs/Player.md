# Player

Tellycine's built-in player uses Android's Media3 (ExoPlayer). It plays HLS, MPEG-TS, DASH and ordinary video files.

## Controls
Tap the video to show the controls.

**Standard playback controls:**
- play/pause and seeking; on movies, episodes and replays, also 10 s back and 30 s forward
- the **settings** button, for audio and subtitle tracks and playback speed

**Top bar:**
- **← Back**
- **Channel up / down** and the **channel list**, on live TV. Switching stays within the category you opened the channel from.
- **Cast**, when a Chromecast is on your network. See [Chromecast](Chromecast.md).
- **Resize:** cycles through **Fit**, **Zoom** and **Stretch**.
- **Stats for nerds:** shows what the player knows about the stream, updated every second: the stream type, video and audio formats, HDR and colour information, the decoders used, download rate, buffer, dropped frames and the screen's resolution and refresh rate. Press it again to hide them.
- **Picture-in-picture:** the video carries on in a small window when you leave the app.

Movies and episodes save your position as you watch, so you can resume later. Live streams that drop are retried automatically a few times.

## External players
- **One stream:** long-press a channel and choose **Open with…**, then pick any installed video app.
- **Everything:** set **Settings → Default player → External app**. Android then asks which app to use, and you can choose one to always use.

Tellycine passes the title, user agent and referrer to apps that support them, such as VLC and MX Player. Dispatcharr recordings always play in the built-in player, because they need a login header other apps can't send.
