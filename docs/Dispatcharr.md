# Dispatcharr

[Dispatcharr](https://github.com/Dispatcharr/Dispatcharr) is a self-hosted IPTV proxy and manager. Mosaic has extra support for it.

## Adding a Dispatcharr server
Add it as **Xtream Codes**:
- **Server:** your Dispatcharr address, e.g. `http://192.168.1.10:9191`.
- **Username and password:** the Xtream password set for your user in Dispatcharr. This isn't the web login password.

Mosaic recognises Dispatcharr automatically. The playlist card then says **Dispatcharr**, and the placeholder expiry date is hidden. [Catch-up](Catch-up.md) works on channels where Dispatcharr has an archive.

If you paste a Dispatcharr `…/output/m3u` link instead, Mosaic suggests switching to the Xtream login. Catch-up, movies and series only come through the Xtream login, not the M3U link.

## Recordings
1. Open the playlist's settings (**⋮ → Edit**) and find **Dispatcharr recordings**.
2. Tap **Connect** and sign in with your Dispatcharr **web login**, or choose **Use an API key instead** and paste a key.

Mosaic stores only your API key, never your password. It reuses the key your account already has rather than creating a new one, so other apps using that key keep working.

Your Dispatcharr user needs DVR access: **view** to watch recordings, **manage** to schedule and delete them.

**Using recordings:**
- **Watch:** go to **Library → Recordings**. Recordings are grouped into *Recording now*, *Scheduled*, *Recorded* and *Failed*. Finished recordings can be seeked; ones still recording play live.
- **Record:** long-press a channel and tap the record button next to a current or upcoming programme. Dispatcharr's pre- and post-padding settings apply.
- **Cancel or delete:** use the ⋮ menu on a recording.

## Sharing stream URLs
From a channel's guide sheet, **Share stream URL** offers:
- **Dispatcharr URL:** the address Mosaic plays, through Dispatcharr.
- **Source URL (from Dispatcharr):** the provider stream behind the channel. If there are several, you choose one.

Sharing the source URL needs the Dispatcharr login connected, and a *Standard* user or above.

## Limitations
- Dispatcharr only serves live channels as raw MPEG-TS, so they usually can't be [cast to a Chromecast](Chromecast.md).
- Recordings play only in Mosaic's built-in player.
