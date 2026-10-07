# Catch-up TV

Catch-up lets you watch programmes that have already aired, if your provider keeps an archive.

## How to use it
1. Look for the history icon next to a channel's name in the Live TV list.
2. Long-press the channel to open its guide.
3. Tap the replay button next to any past programme still inside the archive. Older programmes appear greyed out.
4. To restart the programme that's on now, tap **From the start**.

Replays play like a recording: you can pause, rewind and fast-forward. They aren't added to your watch history.

## Supported providers
| Source | How Mosaic requests the archive |
| --- | --- |
| **Xtream Codes** (including Dispatcharr) | The standard `timeshift` URL. The programme start time is converted to the server's timezone; Dispatcharr uses UTC. |
| **M3U** | `catchup` / `catchup-source` attributes on the playlist or on individual channels: `default` (a URL template), `append`, `shift` and `flussonic`. Templates can use `{utc}`, `{start}`, `{utcend}`, `{lutc}`, `{duration}`, `{offset}`, `{Y}`, `{m}`, `{d}`, `{H}`, `{M}`, `{S}` and their `${…}` forms. |

The archive length comes from the provider's `tv_archive_duration`, or `catchup-days` in M3U playlists (7 days if not stated). Mosaic keeps up to 7 days of past guide data for these channels.
