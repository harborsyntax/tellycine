# Dispatcharr

[Dispatcharr](https://github.com/Dispatcharr/Dispatcharr) is a self-hosted IPTV proxy and manager. Tellycine has extra support for it.

## Adding a Dispatcharr server
Add it as **Xtream Codes**:
- **Server:** your Dispatcharr address, e.g. `http://192.168.1.10:9191`.
- **Username and password:** the Xtream password set for your user in Dispatcharr. This isn't the web login password.

Tellycine recognises Dispatcharr automatically. The playlist card then says **Dispatcharr**, and the placeholder expiry date is hidden. [Catch-up](Catch-up.md) works on channels where Dispatcharr has an archive.

If you paste a Dispatcharr `…/output/m3u` link instead, Tellycine suggests switching to the Xtream login. Catch-up, movies and series only come through the Xtream login, not the M3U link.

## Recordings
1. Open the playlist's settings (**⋮ → Edit**) and find **Dispatcharr recordings**.
2. Tap **Connect** and sign in with your Dispatcharr **web login**, or choose **Use an API key instead** and paste a key.

Tellycine stores only your API key, never your password. It reuses the key your account already has rather than creating a new one, so other apps using that key keep working.

Your Dispatcharr user needs DVR access: **view** to watch recordings, **manage** to schedule and delete them.

**Using recordings:**
- **Watch:** go to **Library → Recordings**. Recordings are grouped into *Recording now*, *Scheduled*, *Recorded* and *Failed*. Finished recordings can be seeked; ones still recording play live.
- **Record:** long-press a channel and tap the record button next to a current or upcoming programme. Dispatcharr's pre- and post-padding settings apply.
- **Cancel or delete:** use the ⋮ menu on a recording.

## Sharing stream URLs
From a channel's guide sheet, **Share stream URL** offers:
- **Dispatcharr URL:** the address Tellycine plays, through Dispatcharr.
- **Source URL (from Dispatcharr):** the provider stream behind the channel. If there are several, you choose one.

Sharing the source URL needs the Dispatcharr login connected, and a *Standard* user or above.

## Limitations
- Casting live channels needs Dispatcharr 0.25 or newer. Older versions only serve raw MPEG-TS, which Chromecast can't play. See [Casting live channels](#casting-live-channels).
- Recordings play only in Tellycine's built-in player.

## Casting live channels
Chromecast can't play the MPEG-TS streams Dispatcharr normally serves. On Dispatcharr 0.25 or newer, Tellycine asks for the channel as fragmented MP4 when casting. Your phone still plays the usual stream.

Many channels carry Dolby (AC3) or MP2 audio, which a Chromecast may play as silence. If you've connected your Dispatcharr login for [recordings](#recordings), Tellycine also asks Dispatcharr to convert the audio to AAC while casting. It uses Dispatcharr's built-in **Web Player (AAC Audio)** output profile, or another active profile that encodes AAC.
- **Who can use it:** looking up output profiles needs a Dispatcharr user at the *Standard* level or above.
- **Without the login,** or if the profile can't be found, channels still cast, but some may have no sound. Alternatively, an admin can set **Web Player (AAC Audio)** as your Dispatcharr user's default output profile.
- **Server load:** each converted channel runs an ffmpeg process on your Dispatcharr server while it's being watched.

