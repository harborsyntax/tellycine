# Live TV & TV guide

<p>
  <img src="images/screens/live-tv.png" width="280" alt="Live TV">
  <img src="images/screens/guide-sheet.png" width="280" alt="Channel guide">
</p>

## Browsing channels
- **Each row** shows the channel number, logo, name, the programme on now and how far through it is.
- **Categories:** on phones they're the chips above the list; tap **☰** to search the full category list. On tablets and in landscape on larger screens, they're in a side pane.

  <img src="images/screens/tablet-live-tv.png" width="600" alt="Tablet layout">
- **All channels** shows everything, and **Favourites** shows your favourite channels.
- **Tap a channel** to watch it. **Tap the heart** to add it to favourites.
- **The history icon** after a name means the channel has [catch-up](Catch-up.md).

## Grid TV guide (landscape)
Turn your phone or tablet sideways and tap the **guide** icon in the top bar for a traditional TV guide. Tap the **list** icon to switch back. Mosaic remembers your choice, and uses the list whenever the screen is upright.

<img src="images/screens/tv-guide-grid.png" width="600" alt="Grid TV guide">

- **Layout:** channels run down the left and time runs across the top, with each programme drawn as a block as long as it lasts. A line marks the current time, and what's on now is highlighted.
- **Scrolling:** swipe up and down through channels, and sideways through time. Every row moves together, and **Now** jumps back to the current time. It goes back two hours and forward as far as your guide goes (**Settings → Guide days**).
- **Tap a programme:**
  - **On now:** watches the channel.
  - **Already shown,** on [catch-up](Catch-up.md) channels (marked with a replay icon): plays it from the start.
  - **Coming up:** opens the channel's guide sheet, where Dispatcharr playlists can record it. A red dot marks programmes already set to record.
- **Tap a channel** on the left to watch it. **Long-press** a channel or programme for the channel's guide sheet.
- **Categories** stay as chips above the guide.

## The channel guide sheet
Long-press a channel to open its guide sheet.

**Buttons along the top:**
- **Watch live**
- **From the start:** restarts the current programme, on catch-up channels.
- **Open with…:** plays the channel in another app, such as VLC or MX Player. See [Player](Player.md).
- **Share stream URL:** shares the channel's stream address. Dispatcharr playlists offer two options; see [Dispatcharr](Dispatcharr.md).

> Shared stream URLs include your login details.

**The schedule underneath** opens at what's on now:
- Each programme shows its time, title and description, and the current one is highlighted.
- On [catch-up](Catch-up.md) channels, past programmes have a **replay** button.
- With [Dispatcharr recordings](Dispatcharr.md) connected, current and upcoming programmes have a **record** button. Tap it again to cancel.

## Where the guide comes from
Mosaic matches channels to the XMLTV guide by `tvg-id`, falling back to the channel name. It keeps the number of days ahead set in [Settings](Settings.md) (1–7), plus up to 7 days back for catch-up channels.
