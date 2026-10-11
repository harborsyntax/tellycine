package app.tellycine.data.repo

import android.content.Context
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.AppSettings
import app.tellycine.data.settings.SettingsRepository
import app.tellycine.data.sync.SyncManager
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class PlaylistRepository(
    private val context: Context,
    private val db: TellycineDatabase,
    private val settings: SettingsRepository,
    private val syncManager: SyncManager,
) {
    private val dao = db.playlists()

    val playlists: Flow<List<PlaylistEntity>> = dao.observeAll()

    /** The selected playlist, falling back to the first one when nothing valid is selected. */
    val activePlaylist: Flow<PlaylistEntity?> =
        combine(playlists, settings.settings) { list, prefs ->
            list.firstOrNull { it.id == prefs.activePlaylistId } ?: list.firstOrNull()
        }.distinctUntilChanged()

    /** The playlists the [kind] section shows: its own choice, or the active playlist. */
    fun scope(kind: ContentKind): Flow<PlaylistScope> =
        combine(playlists, settings.settings) { list, prefs ->
            val active = list.firstOrNull { it.id == prefs.activePlaylistId } ?: list.firstOrNull()
            PlaylistScope.of(list, active, prefs.sectionPlaylists[kind])
        }.distinctUntilChanged()

    /** Shows [id] in the [kind] section, or every playlist with [AppSettings.ALL_PLAYLISTS]. */
    suspend fun setSectionPlaylists(kind: ContentKind, id: Long) = settings.setSectionPlaylists(kind, id)

    suspend fun get(id: Long) = dao.get(id)

    /** Uses [id] everywhere: as the active playlist, and in each section, whatever it showed before. */
    suspend fun setActive(id: Long) {
        settings.setActivePlaylist(id)
        settings.clearSectionPlaylists()
    }

    /** Saves a new or edited playlist and starts a refresh. */
    suspend fun save(playlist: PlaylistEntity): Long {
        val id = if (playlist.id == 0L) {
            dao.insert(playlist)
        } else {
            val existing = dao.get(playlist.id)
            // A replaced playlist file that was copied into app storage isn't needed any more.
            if (existing?.type == PlaylistType.M3U_FILE && existing.url != playlist.url) PlaylistFiles.delete(context, existing.url)
            dao.upsert(
                playlist.copy(
                    detectedEpgUrl = existing?.detectedEpgUrl.orEmpty(),
                    lastSyncedAt = existing?.lastSyncedAt,
                    epgSyncedAt = existing?.epgSyncedAt,
                    liveCount = existing?.liveCount ?: 0,
                    movieCount = existing?.movieCount ?: 0,
                    seriesCount = existing?.seriesCount ?: 0,
                    programmeCount = existing?.programmeCount ?: 0,
                    expiresAt = existing?.expiresAt,
                    maxConnections = existing?.maxConnections,
                    serverTimezone = existing?.serverTimezone,
                    serverSoftware = existing?.serverSoftware,
                    dvrApiKey = existing?.dvrApiKey,
                    dvrUsername = existing?.dvrUsername,
                    createdAt = existing?.createdAt ?: playlist.createdAt,
                )
            )
            playlist.id
        }
        if (settings.current().activePlaylistId == null || playlist.id == 0L) settings.setActivePlaylist(id)
        syncManager.sync(id)
        return id
    }

    suspend fun delete(id: Long) {
        syncManager.cancel(id)
        dao.get(id)?.takeIf { it.type == PlaylistType.M3U_FILE }?.let { PlaylistFiles.delete(context, it.url) }
        val content = db.content()
        db.withTransaction {
            content.deleteStreams(id)
            content.deleteCategories(id)
            content.deleteEpisodes(id)
            content.deleteProgrammes(id)
            content.deleteFavorites(id)
            content.deleteHistory(id)
            content.deleteHiddenCategories(id)
            dao.delete(id)
        }
        if (settings.current().activePlaylistId == id) {
            settings.setActivePlaylist(dao.getAll().firstOrNull()?.id)
        }
    }
}
