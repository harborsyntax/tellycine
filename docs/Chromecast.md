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
| Raw MPEG-TS streams, including **Dispatcharr live channels** | ❌ Chromecast can't play raw TS |
| Streams that need a specific user agent or referrer | ⚠️ Often fail, because the TV fetches the stream itself |
| Dispatcharr recordings | ❌ They play on your phone only |

Casting needs Google Play services on your phone. Without them, the Cast button doesn't appear.
