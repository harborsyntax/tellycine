package app.tellycine.core

import app.tellycine.core.tmdb.Tmdb
import app.tellycine.core.tmdb.TmdbEpisodeRef
import app.tellycine.core.tmdb.TmdbKind
import app.tellycine.core.tmdb.TmdbTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {
    private fun result(id: Int, title: String, date: String?, original: String? = null) =
        TmdbTitle(id, title, original, date, null, null, null, null, 0)

    @Test
    fun readsSearchResults() {
        val movies = Tmdb.parseSearch(
            """{"page":1,"results":[
                 {"id":603,"title":"The Matrix","original_title":"The Matrix","release_date":"1999-03-30","overview":"Neo…",
                  "poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.2,"vote_count":26000},
                 {"id":9,"title":"Unrated","release_date":"","vote_average":0,"vote_count":0},
                 {"title":"No id"}]}""",
            TmdbKind.MOVIE,
        )
        assertEquals(listOf(603, 9), movies.map { it.id })
        assertEquals(8.2, movies[0].rating!!, 0.001)
        assertEquals("1999-03-30", movies[0].date)
        // No votes, no rating; an empty date is no date.
        assertNull(movies[1].rating)
        assertNull(movies[1].date)

        val shows = Tmdb.parseSearch("""{"results":[{"id":1399,"name":"Game of Thrones","original_name":"Game of Thrones","first_air_date":"2011-04-17"}]}""", TmdbKind.TV)
        assertEquals("Game of Thrones", shows.single().title)
        assertEquals("2011-04-17", shows.single().date)
    }

    @Test
    fun readsAFilmsPageWithEverythingAppended() {
        val film = Tmdb.parseDetails(
            """{"id":603,"title":"The Matrix","original_title":"The Matrix","tagline":"Welcome to the Real World.",
               "overview":"A hacker…","release_date":"1999-03-30","runtime":136,"status":"Released",
               "genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}],
               "vote_average":8.2,"vote_count":26000,"poster_path":"/poster.jpg","backdrop_path":"/main.jpg",
               "belongs_to_collection":{"id":2344,"name":"The Matrix Collection","poster_path":"/c.jpg"},
               "credits":{"cast":[{"id":2,"name":"Carrie-Anne Moss","character":"Trinity","order":2,"profile_path":"/cam.jpg"},
                                  {"id":1,"name":"Keanu Reeves","character":"Neo","order":0,"profile_path":"/kr.jpg"}],
                          "crew":[{"name":"Lana Wachowski","job":"Director","department":"Directing"},
                                  {"name":"Lilly Wachowski","job":"Director","department":"Directing"},
                                  {"name":"Lana Wachowski","job":"Writer","department":"Writing"},
                                  {"name":"Joel Silver","job":"Producer","department":"Production"}]},
               "videos":{"results":[{"key":"teaserKey01","site":"YouTube","type":"Teaser","official":true},
                                    {"key":"vimeo","site":"Vimeo","type":"Trailer","official":true},
                                    {"key":"trailerKey1","site":"YouTube","type":"Trailer","official":true}]},
               "images":{"backdrops":[{"file_path":"/words.jpg","iso_639_1":"en"},{"file_path":"/clean.jpg","iso_639_1":null},
                                      {"file_path":"/main.jpg","iso_639_1":null}]},
               "release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"R","type":3}]},
                                           {"iso_3166_1":"GB","release_dates":[{"certification":"","type":1},{"certification":"15","type":3}]}]},
               "recommendations":{"results":[{"id":604,"title":"The Matrix Reloaded","release_date":"2003-05-15"}]}}""",
            TmdbKind.MOVIE,
            region = "GB",
        )!!
        assertEquals("Welcome to the Real World.", film.tagline)
        assertEquals(136, film.runtime)
        assertEquals(listOf("Action", "Science Fiction"), film.genres)
        assertEquals("15", film.certification)
        assertEquals(listOf("Lana Wachowski", "Lilly Wachowski"), film.directors)
        assertEquals(listOf("Lana Wachowski"), film.writers)
        // Cast in billing order.
        assertEquals(listOf("Keanu Reeves", "Carrie-Anne Moss"), film.cast.map { it.name })
        assertEquals("Neo", film.cast.first().role)
        assertEquals("https://www.youtube.com/watch?v=trailerKey1", film.trailer)
        // The main backdrop first, then those without words.
        assertEquals(listOf("/main.jpg", "/clean.jpg", "/words.jpg"), film.backdrops)
        assertEquals("The Matrix Collection", film.collection?.name)
        assertEquals(listOf(604), film.recommendations.map { it.id })
        assertFalse(film.ended)
    }

    @Test
    fun readsASeriesPage() {
        val show = Tmdb.parseDetails(
            """{"id":1399,"name":"Show","first_air_date":"2011-04-17","last_air_date":"2019-05-19","status":"Ended",
               "episode_run_time":[60],"number_of_seasons":8,"number_of_episodes":73,
               "created_by":[{"name":"David Benioff"},{"name":"D. B. Weiss"}],"networks":[{"name":"HBO"}],
               "last_episode_to_air":{"season_number":8,"episode_number":6,"name":"The Iron Throne","air_date":"2019-05-19"},
               "next_episode_to_air":null,
               "seasons":[{"season_number":0,"name":"Specials","episode_count":3},{"season_number":1,"name":"Season 1","poster_path":"/s1.jpg","episode_count":10}],
               "aggregate_credits":{"cast":[{"id":5,"name":"Emilia Clarke","order":1,"roles":[{"character":"Daenerys"}]},
                                            {"id":6,"name":"Kit Harington","order":0,"roles":[{"character":"Jon Snow"}]}]},
               "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]}}""",
            TmdbKind.TV,
            region = "GB",
        )!!
        assertTrue(show.ended)
        assertEquals("2019-05-19", show.lastAired)
        assertEquals(TmdbEpisodeRef(8, 6, "The Iron Throne", "2019-05-19"), show.lastEpisode)
        assertNull(show.nextEpisode)
        assertEquals(60, show.runtime)
        assertEquals(listOf("David Benioff", "D. B. Weiss"), show.creators)
        assertEquals(listOf("HBO"), show.networks)
        assertEquals(listOf("Kit Harington", "Emilia Clarke"), show.cast.map { it.name })
        assertEquals("Jon Snow", show.cast.first().role)
        // No British rating: the US one.
        assertEquals("TV-MA", show.certification)
        assertEquals(listOf(0, 1), show.seasons.map { it.number })
        assertEquals(8, show.seasonCount)
    }

    @Test
    fun readsASeasonAndACollection() {
        val season = Tmdb.parseSeason(
            """{"season_number":2,"name":"Season 2","overview":"o","episodes":[
                 {"episode_number":1,"season_number":2,"name":"The North Remembers","overview":"…","still_path":"/e1.jpg","air_date":"2012-04-01","runtime":53},
                 {"episode_number":2,"name":"The Night Lands","runtime":0}]}"""
        )!!
        assertEquals(listOf(1, 2), season.episodes.map { it.number })
        assertEquals("The North Remembers", season.episodes[0].name)
        assertEquals(53, season.episodes[0].runtime)
        assertNull(season.episodes[1].runtime)
        assertEquals(2, season.episodes[1].season)

        val collection = Tmdb.parseCollection(
            """{"id":2344,"name":"The Matrix Collection","parts":[
                 {"id":604,"title":"The Matrix Reloaded","release_date":"2003-05-15"},
                 {"id":603,"title":"The Matrix","release_date":"1999-03-30"},
                 {"id":999,"title":"Untitled","release_date":""}]}"""
        )!!
        // In order of release; a film without a date yet last.
        assertEquals(listOf(603, 604, 999), collection.parts.map { it.id })
    }

    @Test
    fun matchesByNameAndYear() {
        val results = listOf(
            result(1, "The Office", "2005-03-24"),
            result(2, "The Office", "2001-07-09"),
            result(3, "La Casa de Papel", "2017-05-02", original = "La casa de papel"),
            result(4, "Money Heist", "2017-05-02", original = "La casa de papel"),
        )
        assertEquals(1, Tmdb.bestMatch(results, "Office", null)?.id)
        assertEquals(2, Tmdb.bestMatch(results, "The Office", 2001)?.id)
        // Providers' years are often a year out.
        assertEquals(2, Tmdb.bestMatch(results, "The Office", 2002)?.id)
        // The original title counts too.
        assertEquals(3, Tmdb.bestMatch(results, "La Casa de Papel", 2017)?.id)
        // A different year is a different title (a remake, say), and a different name isn't it.
        assertNull(Tmdb.bestMatch(results, "The Office", 1990))
        assertNull(Tmdb.bestMatch(results, "Office Space", null))
    }

    @Test
    fun buildsImageAddresses() {
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", Tmdb.image("/p.jpg", Tmdb.POSTER))
        assertNull(Tmdb.image(null, Tmdb.POSTER))
        assertNull(Tmdb.image("p.jpg", Tmdb.POSTER))
    }
}
