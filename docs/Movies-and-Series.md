# Movies & series

<p>
  <img src="images/screens/movies.png" width="260" alt="Movies">
  <img src="images/screens/movie-detail.png" width="260" alt="Movie details">
  <img src="images/screens/series-detail.png" width="260" alt="Series details">
</p>

## Browsing
- **Layout:** the **Movies** and **Series** tabs show poster grids by category, the same way as [Live TV](Live-TV.md). On tablets, categories are in a side pane.
- **Posters** show the title, year, genre and rating. With [details from TMDB](#details-from-tmdb) on, titles without a poster get TMDB's, and the rating is TMDB's.
- **Hidden tabs:** if a playlist has [movies & series turned off](Managing-Playlists.md), these tabs are hidden.
- **Playlists:** with more than one playlist, the button before the categories chooses **All playlists** or one of them, separately for Movies and for Series. Together, each poster shows the playlist it's from, and a title's page always says which playlist it's from. See [Switching playlists](Managing-Playlists.md#switching-playlists).
- **Sort:** the sort button (at the top of the screen; on a TV, beside the category button) puts titles in another order. The current order has an arrow beside it: **down** for newest first (or Z to A), **up** for oldest first (or A to Z). Press the current order again to turn it round; the menu stays open so you can see the arrow change. Another order starts the usual way round, shown first below. Movies and Series each remember their own, and start on **Date added**, newest first:
  - **Date added:** newest first. Xtream Codes providers say when they added each movie; otherwise (M3U playlists, and most series) it's when Tellycine first saw the title, so after the first import, new arrivals come to the top.
  - **Release date:** newest first, from the provider's release date or year. In M3U playlists, a year in brackets in the title counts, as in "Film Name (2023)".
  - **Latest episode** (series): the series that gained episodes most recently first. Xtream Codes providers give a date for this; for M3U playlists, it's when Tellycine saw a series gain episodes at a refresh, so it means something from the second refresh on.
  - **Last aired** (series, with [details from TMDB](#details-from-tmdb) turned on): by when the latest episode was broadcast, rather than when your provider added it.
  - **Rating** (with details from TMDB turned on): TMDB's score, highest first.
  - **Title:** A to Z.
  - **Provider order:** as your provider lists them (turned round, from the end of the list).

  Titles without the date sorted by come after the rest, in the provider's order, whichever way round.

<img src="images/screens/tablet-movies.png" width="600" alt="Tablet movies">

## Details from TMDB
Tellycine can add what [TMDB](https://www.themoviedb.org) (The Movie Database) knows about your movies and series. It's off unless you turn on **Details from TMDB** in [Settings](Settings.md), and it needs an API key of your own: Tellycine doesn't come with one.
- **Getting a key:** TMDB's keys are free for personal use. Sign in at [themoviedb.org](https://www.themoviedb.org) (or make an account), open **Settings → API** and request a developer key. Then copy the **API Key**, or the longer **API Read Access Token**, into **TMDB API key** in Tellycine's settings. Tellycine checks the key with TMDB before keeping it.
- **In the lists:** posters for titles without one, TMDB's ratings, and two more sorts: **Rating**, and for series **Last aired**.
- **On a title's page:** its tagline and original title, TMDB's rating and the age rating (for your device's country), genres, the plot when your provider has none, TMDB's backdrops, and who made it: the director and writers of a film, or who created a series and its network. The cast is shown with photos and the characters they play.
- **Series** also show whether they're still running and when the next episode is out, each season's overview, and, for each episode, its air date and the name, plot and picture where your provider has none.
- **More to watch:** a film's other parts (its collection) and titles **like this one**, as far as your playlists have them. Choose one to open it.
- **Language:** in your device's language where TMDB has a translation, otherwise English.
- **How it matches:** by the TMDB id when an Xtream Codes provider gives one, otherwise by name and year, after taking off the codes and tags providers add (as in "EN - Show Name (2019) [4K]"). The same title in several playlists is looked up once. Titles it can't match keep just what your provider says, and sort last.
- **The first look-up** takes a while with many titles. After that, Tellycine only asks about new titles, series with a new episode out or that gained episodes, and, now and then, the rest: running series weekly, films and finished series every three months, and titles TMDB didn't know monthly. It checks after each refresh and when the app starts. A title's page asks TMDB for its full details when you open it.
- **Progress:** the Playlists page shows how it's going, with **Check now**. See [Managing playlists](Managing-Playlists.md#details-from-tmdb).
- **Kept for six months at most,** as TMDB's terms ask. Turning it off removes TMDB's posters, ratings and air dates from the lists.

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Details
Tap a poster to open its details: artwork, rating, year, length, genre, plot, director and cast. Xtream accounts load the extra details from the provider, and [details from TMDB](#details-from-tmdb) add more.
- **Artwork:** the title's backdrop fills the top of the page. When the provider has several, they take turns, fading from one to the next and slowly zooming in (unless **Remove animations** is on in Android's accessibility settings). Without a backdrop, a blurred copy of the poster is used instead.
- **Colours:** the page takes its colours from the artwork, keeping the app's light or dark look.
- **Play / Resume:** carries on where you stopped. A progress bar shows how far you got.
- **Heart:** adds the title to favourites.
- **Trailer** (film strip): opens the trailer in the YouTube app, when the provider (or TMDB) has one.
- **Download:** saves the movie to your device to watch offline. See [Downloads](Downloads.md).

## Series
- **Seasons and episodes:** choose a season, then tap an episode. When the provider has season posters, the seasons are shown as posters; otherwise as tabs.
- **Main button:** offers the next episode to watch, or resumes the one you're part-way through.
- **Progress:** partly watched episodes show a progress bar.
- **Download:** each episode has its own download button. See [Downloads](Downloads.md).

Xtream accounts load seasons and episodes from the provider. For M3U playlists, Tellycine groups episodes into shows using the `S01E02` pattern in their names.
