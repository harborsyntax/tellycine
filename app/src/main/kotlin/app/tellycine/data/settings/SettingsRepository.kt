package app.tellycine.data.settings

import android.content.Context
import app.tellycine.core.ContentKind
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour themes for the TV interface, chosen in its Settings. */
enum class TvTheme(val label: String, val description: String) {
    WARM("Warm", "Amber on dark brown, as Tellycine has always looked"),
    MIDNIGHT("Midnight", "Cool white and sky blue on blue-black"),
    GRAPHITE("Graphite", "Pure white and teal on neutral grey, with no colour cast"),
    FOREST("Forest", "Soft green on deep green"),
    HIGH_CONTRAST("High contrast", "White and yellow on black, easiest to read from across the room"),
}

/** Ordering for the Library's favourites and history rows. */
enum class LibrarySort { RECENT, NAME_ASC, NAME_DESC, PLAYLIST_ORDER }

data class AppSettings(
    val activePlaylistId: Long? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val epgDays: Int = 3,
    val refreshOnLaunch: Boolean = true,
    val preferHls: Boolean = false,
    /** Hand playback to another app instead of the built-in player. */
    val externalPlayer: Boolean = false,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    /** Split Library favourites and history into one row per category. */
    val libraryGroupByCategory: Boolean = false,
    /** Only download movies and episodes over unmetered networks (usually Wi-Fi). */
    val downloadWifiOnly: Boolean = true,
    /** Show Live TV as a grid guide (channels by time) when the screen is landscape. */
    val liveGuideGrid: Boolean = false,
    /** TV: open on the last channel watched when the app starts. */
    val startOnLastChannel: Boolean = false,
    /** TV: switch the display's refresh rate to suit the video (e.g. 50 Hz for 25/50 fps channels). */
    val matchFrameRate: Boolean = false,
    /** TV: shorter guide rows, so more channels fit on screen. */
    val tvGuideCompact: Boolean = false,
    /** TV: the interface's colours. */
    val tvTheme: TvTheme = TvTheme.WARM,
    /** TV: the multi-view layout used last (a MultiViewLayout name). */
    val multiViewLayout: String? = null,
    /**
     * The playlists Live TV, Movies and Series each show: a playlist's id, [ALL_PLAYLISTS], or
     * null to follow the active playlist.
     */
    val sectionPlaylists: Map<ContentKind, Long> = emptyMap(),
) {
    companion object {
        /** A section showing every playlist together. */
        const val ALL_PLAYLISTS = -1L
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val activePlaylist = longPreferencesKey("active_playlist")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val epgDays = intPreferencesKey("epg_days")
        val refreshOnLaunch = booleanPreferencesKey("refresh_on_launch")
        val preferHls = booleanPreferencesKey("prefer_hls")
        val externalPlayer = booleanPreferencesKey("external_player")
        val librarySort = stringPreferencesKey("library_sort")
        val libraryGroup = booleanPreferencesKey("library_group_by_category")
        val downloadWifiOnly = booleanPreferencesKey("download_wifi_only")
        val liveGuideGrid = booleanPreferencesKey("live_guide_grid")
        val startOnLastChannel = booleanPreferencesKey("start_on_last_channel")
        val matchFrameRate = booleanPreferencesKey("match_frame_rate")
        val tvGuideCompact = booleanPreferencesKey("tv_guide_compact")
        val tvTheme = stringPreferencesKey("tv_theme")
        val multiViewLayout = stringPreferencesKey("multi_view_layout")
        val sectionPlaylists = ContentKind.entries.associateWith { longPreferencesKey("playlists_${it.name.lowercase()}") }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            activePlaylistId = p[Keys.activePlaylist],
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            epgDays = p[Keys.epgDays] ?: 3,
            refreshOnLaunch = p[Keys.refreshOnLaunch] ?: true,
            preferHls = p[Keys.preferHls] ?: false,
            externalPlayer = p[Keys.externalPlayer] ?: false,
            librarySort = p[Keys.librarySort]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT,
            libraryGroupByCategory = p[Keys.libraryGroup] ?: false,
            downloadWifiOnly = p[Keys.downloadWifiOnly] ?: true,
            liveGuideGrid = p[Keys.liveGuideGrid] ?: false,
            startOnLastChannel = p[Keys.startOnLastChannel] ?: false,
            matchFrameRate = p[Keys.matchFrameRate] ?: false,
            tvGuideCompact = p[Keys.tvGuideCompact] ?: false,
            tvTheme = p[Keys.tvTheme]?.let { runCatching { TvTheme.valueOf(it) }.getOrNull() } ?: TvTheme.WARM,
            multiViewLayout = p[Keys.multiViewLayout],
            sectionPlaylists = Keys.sectionPlaylists.mapNotNull { (kind, key) -> p[key]?.let { kind to it } }.toMap(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setActivePlaylist(id: Long?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.activePlaylist) else it[Keys.activePlaylist] = id
    }

    /** Which playlists the [kind] section shows: an id, [AppSettings.ALL_PLAYLISTS], or null to follow the active one. */
    suspend fun setSectionPlaylists(kind: ContentKind, id: Long?) = context.dataStore.edit {
        val key = Keys.sectionPlaylists.getValue(kind)
        if (id == null) it.remove(key) else it[key] = id
    }

    /** Every section follows the active playlist again. */
    suspend fun clearSectionPlaylists() = context.dataStore.edit { prefs -> Keys.sectionPlaylists.values.forEach { prefs.remove(it) } }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setEpgDays(days: Int) = context.dataStore.edit { it[Keys.epgDays] = days }
    suspend fun setRefreshOnLaunch(enabled: Boolean) = context.dataStore.edit { it[Keys.refreshOnLaunch] = enabled }
    suspend fun setPreferHls(enabled: Boolean) = context.dataStore.edit { it[Keys.preferHls] = enabled }
    suspend fun setExternalPlayer(enabled: Boolean) = context.dataStore.edit { it[Keys.externalPlayer] = enabled }
    suspend fun setLibrarySort(sort: LibrarySort) = context.dataStore.edit { it[Keys.librarySort] = sort.name }
    suspend fun setLibraryGroupByCategory(enabled: Boolean) = context.dataStore.edit { it[Keys.libraryGroup] = enabled }
    suspend fun setDownloadWifiOnly(enabled: Boolean) = context.dataStore.edit { it[Keys.downloadWifiOnly] = enabled }
    suspend fun setLiveGuideGrid(enabled: Boolean) = context.dataStore.edit { it[Keys.liveGuideGrid] = enabled }
    suspend fun setStartOnLastChannel(enabled: Boolean) = context.dataStore.edit { it[Keys.startOnLastChannel] = enabled }
    suspend fun setMatchFrameRate(enabled: Boolean) = context.dataStore.edit { it[Keys.matchFrameRate] = enabled }
    suspend fun setTvGuideCompact(enabled: Boolean) = context.dataStore.edit { it[Keys.tvGuideCompact] = enabled }
    suspend fun setTvTheme(theme: TvTheme) = context.dataStore.edit { it[Keys.tvTheme] = theme.name }
    suspend fun setMultiViewLayout(name: String) = context.dataStore.edit { it[Keys.multiViewLayout] = name }
}
