# Troubleshooting

## Installing
**"App not installed" when updating**
Versions before 1.4.2 were signed with a temporary key. Uninstall once, then install the latest version. Later updates install over the top.

**Play Protect warns about an unknown developer**
This is normal for apps installed outside the Play Store. Tap **More details → Install anyway**.

## Playlists
**Login failed**
For Xtream Codes, check the server address (including `http://` and the port), the username and the password. For Dispatcharr, use the user's *Xtream* password, not the web login.

**HTTP 401 / 403 while loading**
Your credentials are wrong, or the provider requires a particular user agent. Pick one under **Edit → Advanced options → User agent**. Try the app your provider recommends (TiviMate and IPTV Smarters Pro are common), or enter a custom one.

**No TV guide, or the wrong guide**
- **Check the guide URL:** make sure the playlist has one. Look under **Edit → Advanced options**, or for `url-tvg` in the M3U file.
- **Check the channel IDs:** channels match the guide by `tvg-id`, then by name. If your playlist's IDs don't match the guide's channel IDs, add the matching guide URL.
- **Try a manual refresh:** use **⋮ → Update TV guide only**.

**The same programme appears more than once in a channel's guide**
Fixed in 1.9.1. Older versions could match one channel to several guide channels with the same name, such as regional or HD versions, and show all their programmes. Tellycine now uses one guide channel per channel, preferring an exact `tvg-id` match, and skips repeated programmes. Guides re-import automatically the next time the app starts. To do it straight away, use **⋮ → Update TV guide only**. If a channel now shows the wrong region's schedule, give it the right `tvg-id` in your playlist.

## Playback
**A channel won't play**
- **Try again:** many IPTV streams drop now and then. Tap **Try again**.
- **Try HLS:** turn on **Settings → Prefer HLS for Xtream live TV**, then refresh the playlist.
- **Try another app:** **Open with…** an external player to see whether the stream works there.

**"This device can't decode the stream"**
The stream uses a codec your phone doesn't support. Try an external player such as VLC.

## Casting
**Cast button missing**
Your phone needs Google Play services, and a Cast device must be on the same Wi-Fi.

**Casting fails**
See [what can't be cast](Chromecast.md#what-cant-be-cast). Raw MPEG-TS streams, including Dispatcharr live channels, and streams that need special headers usually won't play on a Chromecast.

## Dispatcharr
**"Your Dispatcharr user doesn't have permission"**
Ask your Dispatcharr admin to give your user DVR access: *view* or *manage*. Sharing source URLs also needs a *Standard* user or above.

## Downloads
**A download says "Waiting for Wi-Fi"**
**Download over Wi-Fi only** is on. Connect to Wi-Fi, or turn the setting off in Settings and start the download again.

**"This title is a live-style stream (HLS or DASH) and can't be downloaded"**
Your provider only offers this title as a stream of short segments, which can't be saved as one file.

**A download failed with "The server returned HTTP 4xx"**
The provider refused the download. Check you can stream the title, and that you aren't already at your provider's connection limit (for example, watching live TV at the same time). Then tap retry.

## Reporting a problem
Open an issue on GitHub, or include the output of `adb logcat` captured while the problem happens.
