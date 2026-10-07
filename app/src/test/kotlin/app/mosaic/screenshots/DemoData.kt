package app.mosaic.screenshots

import app.mosaic.AppContainer
import app.mosaic.core.ContentKind
import app.mosaic.data.db.CategoryEntity
import app.mosaic.data.db.EpisodeEntity
import app.mosaic.data.db.FavoriteEntity
import app.mosaic.data.db.HistoryEntity
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.PlaylistType
import app.mosaic.data.db.ProgrammeEntity
import app.mosaic.data.db.StreamEntity
import app.mosaic.data.repo.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** A fictional demo playlist so documentation screenshots show a realistic, populated app. */
object DemoData {
    private const val HOUR = 60 * 60 * 1000L

    private val liveCategories = listOf("news" to "News", "sport" to "Sport", "ent" to "Entertainment", "kids" to "Kids", "docs" to "Documentaries", "music" to "Music")
    private val channels = listOf(
        Triple("Mosaic News 24", "news", listOf("World Tonight", "Business Briefing", "The Morning Edition", "Headlines at Ten")),
        Triple("Global Report", "news", listOf("Global Report Live", "Climate Desk", "Market Watch", "Late Edition")),
        Triple("City Sports 1", "sport", listOf("Premier Football Live", "Match Analysis", "Goals of the Week", "Golf Highlights")),
        Triple("City Sports 2", "sport", listOf("Tennis: Quarter-finals", "Cycling Tour Stage 12", "Motorsport Weekly", "Boxing Classics")),
        Triple("Arena Extra", "sport", listOf("Basketball Tonight", "Rugby Review", "Athletics Gala", "Sports Quiz")),
        Triple("Channel Bright", "ent", listOf("The Great Bake Off", "Quiz Night", "Evening Drama", "Late Show")),
        Triple("Lumen TV", "ent", listOf("Home Makeover", "Comedy Hour", "Talent Search", "Movie: Summer Lights")),
        Triple("Panorama One", "ent", listOf("Travel Diaries", "Cooking Together", "Reality Island", "Night Cinema")),
        Triple("Tiny Tales", "kids", listOf("Bluebell Farm", "Robot Friends", "Story Time", "Cartoon Club")),
        Triple("Discovery Lane", "docs", listOf("Ocean Giants", "Ancient Cities", "Wild Mountains", "How Things Work")),
        Triple("Planet Wild", "docs", listOf("Savannah Life", "Deep Forests", "Polar Seasons", "Night Hunters")),
        Triple("Groove FM TV", "music", listOf("Top 40 Countdown", "Live Sessions", "Classic Hits", "Chill Mix")),
    )
    private val movies = listOf(
        listOf("Midnight Harbor", "Drama", "2023", "7.8"), listOf("The Last Signal", "Sci-Fi", "2024", "8.1"),
        listOf("Paper Kites", "Comedy", "2022", "6.9"), listOf("Iron Valley", "Action", "2021", "7.2"),
        listOf("Northern Lights", "Drama", "2020", "7.5"), listOf("Orbit", "Sci-Fi", "2023", "7.9"),
        listOf("Double Shift", "Action", "2024", "6.8"), listOf("The Wedding Plan", "Comedy", "2021", "6.5"),
        listOf("Glass Garden", "Drama", "2022", "8.0"), listOf("Velocity", "Action", "2020", "7.0"),
        listOf("Silent Moon", "Sci-Fi", "2021", "7.4"), listOf("Second Chances", "Comedy", "2023", "7.1"),
    )
    private val series = listOf(
        listOf("The Long Night", "Crime", "2022"), listOf("Harbour Lane", "Drama", "2021"),
        listOf("Office Hours", "Comedy", "2023"), listOf("Cold Case Unit", "Crime", "2020"),
        listOf("Kingdom of Glass", "Drama", "2024"), listOf("Flatmates", "Comedy", "2022"),
    )

    // Room forbids blocking database calls on the main thread, which Robolectric tests run on.
    fun seed(container: AppContainer, groupLibrary: Boolean = false): Long = runBlocking(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = container.database
        val pid = db.playlists().insert(
            PlaylistEntity(
                name = "Demo IPTV",
                type = PlaylistType.M3U_URL,
                url = "https://example.com/demo.m3u",
                lastSyncedAt = now,
                epgSyncedAt = now,
                liveCount = channels.size,
                movieCount = movies.size,
                seriesCount = series.size,
                programmeCount = channels.size * 24,
            )
        )
        container.settings.setRefreshOnLaunch(false)
        container.settings.setActivePlaylist(pid)
        container.settings.setLibraryGroupByCategory(groupLibrary)
        val dao = db.content()

        dao.insertCategories(
            liveCategories.mapIndexed { i, (id, name) -> CategoryEntity(pid, ContentKind.LIVE, id, name, i, channels.count { it.second == id }) } +
                movies.map { it[1] }.distinct().mapIndexed { i, g -> CategoryEntity(pid, ContentKind.MOVIE, g, g, i, movies.count { it[1] == g }) } +
                series.map { it[1] }.distinct().mapIndexed { i, g -> CategoryEntity(pid, ContentKind.SERIES, g, g, i, series.count { it[1] == g }) }
        )
        dao.insertStreams(channels.mapIndexed { i, (name, cat, _) ->
            StreamEntity(
                playlistId = pid, kind = ContentKind.LIVE, streamId = "ch$i", name = name, sortOrder = i, categoryId = cat,
                logo = null, url = "https://example.com/live/$i.m3u8", epgChannelId = "ch$i", number = 101 + i,
                catchupDays = if (cat == "news" || cat == "sport") 7 else null,
                catchupType = if (cat == "news" || cat == "sport") "shift" else null,
            )
        })
        dao.insertStreams(movies.mapIndexed { i, (name, genre, year, rating) ->
            StreamEntity(
                playlistId = pid, kind = ContentKind.MOVIE, streamId = "mv$i", name = name, sortOrder = i, categoryId = genre,
                logo = null, url = "https://example.com/movie/$i.mp4", year = year, genre = genre, rating = rating.toDouble(),
                plot = "A gripping $genre story about choices, chance and the people who change everything.",
            )
        })
        dao.insertStreams(series.mapIndexed { i, (name, genre, year) ->
            StreamEntity(playlistId = pid, kind = ContentKind.SERIES, streamId = "sr$i", name = name, sortOrder = i, categoryId = genre, logo = null, url = "", year = year, genre = genre)
        })
        dao.insertEpisodes((1..2).flatMap { season ->
            (1..6).map { ep ->
                EpisodeEntity(pid, "ep-$season-$ep", "sr0", season, ep, "The Long Night S${season}E$ep", "https://example.com/series/$season$ep.mp4", null)
            }
        })

        // A guide from 3 hours ago to 12 hours ahead, offset per channel so programmes overlap realistically.
        val programmes = ArrayList<ProgrammeEntity>()
        channels.forEachIndexed { i, (_, _, titles) ->
            var start = now - 3 * HOUR - (i % 4) * 15 * 60 * 1000L
            var n = 0
            while (start < now + 12 * HOUR) {
                val length = if (n % 3 == 0) HOUR else 30 * 60 * 1000L
                programmes += ProgrammeEntity(
                    playlistId = pid, channelId = "ch$i", startMs = start, stopMs = start + length,
                    title = titles[n % titles.size], description = "${titles[n % titles.size]}: tonight's episode.", category = null,
                )
                start += length
                n++
            }
        }
        dao.insertProgrammes(programmes)

        listOf(0, 2, 5, 9).forEachIndexed { i, idx -> dao.addFavorite(FavoriteEntity(pid, ContentKind.LIVE, "ch$idx", now - i * HOUR)) }
        listOf(1, 5, 8).forEachIndexed { i, idx -> dao.addFavorite(FavoriteEntity(pid, ContentKind.MOVIE, "mv$idx", now - i * HOUR)) }
        listOf(0, 4).forEachIndexed { i, idx -> dao.addFavorite(FavoriteEntity(pid, ContentKind.SERIES, "sr$idx", now - i * HOUR)) }

        listOf(2, 0, 6, 10, 3).forEachIndexed { i, idx ->
            dao.upsertHistory(
                HistoryEntity(pid, ContentRepository.liveKey("ch$idx"), ContentKind.LIVE, "ch$idx", channels[idx].first, null, null, "", 0, 0, now - i * HOUR)
            )
        }
        dao.upsertHistory(HistoryEntity(pid, ContentRepository.movieKey("mv1"), ContentKind.MOVIE, "mv1", "The Last Signal", "2024", null, "", 52 * 60_000L, 118 * 60_000L, now - HOUR))
        dao.upsertHistory(HistoryEntity(pid, ContentRepository.episodeKey("ep-1-3"), ContentKind.SERIES, "sr0", "The Long Night", "S1 · E3 · The Long Night S1E3", null, "", 21 * 60_000L, 48 * 60_000L, now - 2 * HOUR))
        pid
    }
}
