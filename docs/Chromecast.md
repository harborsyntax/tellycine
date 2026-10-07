# Chromecast

## Casting
1. Make sure your phone and Chromecast or Google TV are on the same Wi-Fi.
2. Tap the **Cast** button in the top bar or in the player, then pick your device.
3. Whatever you're watching moves to the TV from the same point. Anything you start while connected plays on the TV.
4. Your phone's player controls act as a remote. Playback carries on if you leave the app, with controls in the notification.
5. To stop casting, tap Cast again and choose **Stop casting**. Playback returns to your phone where it left off.

## What can't be cast
| Content | Casts? |
| --- | --- |
| HLS (`.m3u8`), DASH, MP4 movies and episodes | ✅ |
| Xtream Codes live channels | ✅ Mosaic switches them to the provider's HLS stream |
| **Dispatcharr live channels** | ✅ On Dispatcharr 0.25 or newer, Mosaic casts its MP4 output. See [Dispatcharr](Dispatcharr.md#casting-live-channels) |
| Other raw MPEG-TS streams | ❌ Chromecast can't play raw TS |
| Streams that need a specific user agent or referrer | ⚠️ Often fail, because the TV fetches the stream itself |
| Dispatcharr recordings | ❌ They play on your phone only |

## Movies with no sound
Many movies, especially MKV files, have Dolby Digital, Dolby Digital Plus, DTS or TrueHD soundtracks. Chromecast plays a file's first soundtrack and can't be told to use another one.
- **Dolby:** Chromecast sends it straight to your TV or soundbar. If they don't support Dolby, you get the picture with no sound. On Google TV models, check the Chromecast's surround sound setting.
- **DTS and TrueHD:** Chromecast can't play them at all.

Before casting a movie or episode, Mosaic checks its soundtracks. If the sound probably won't play, it says so on the player screen while casting, and mentions any other soundtrack in the file, such as AAC. To hear these titles, watch them on your phone or tablet instead, or pick a version with AAC sound if your provider has one.

Casting needs Google Play services on your phone. Without them, the Cast button doesn't appear.
