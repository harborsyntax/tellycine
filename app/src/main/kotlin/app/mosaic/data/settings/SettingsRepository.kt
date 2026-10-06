package app.mosaic.data.settings

import android.content.Context
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

data class AppSettings(
    val activePlaylistId: Long? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val epgDays: Int = 3,
    val refreshOnLaunch: Boolean = true,
    val preferHls: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val activePlaylist = longPreferencesKey("active_playlist")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val epgDays = intPreferencesKey("epg_days")
        val refreshOnLaunch = booleanPreferencesKey("refresh_on_launch")
        val preferHls = booleanPreferencesKey("prefer_hls")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            activePlaylistId = p[Keys.activePlaylist],
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            epgDays = p[Keys.epgDays] ?: 3,
            refreshOnLaunch = p[Keys.refreshOnLaunch] ?: true,
            preferHls = p[Keys.preferHls] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setActivePlaylist(id: Long?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.activePlaylist) else it[Keys.activePlaylist] = id
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setEpgDays(days: Int) = context.dataStore.edit { it[Keys.epgDays] = days }
    suspend fun setRefreshOnLaunch(enabled: Boolean) = context.dataStore.edit { it[Keys.refreshOnLaunch] = enabled }
    suspend fun setPreferHls(enabled: Boolean) = context.dataStore.edit { it[Keys.preferHls] = enabled }
}
