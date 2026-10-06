package app.mosaic.data.repo

import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.MosaicDatabase
import app.mosaic.data.settings.SettingsRepository
import app.mosaic.data.sync.SyncManager
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class PlaylistRepository(
    private val db: MosaicDatabase,
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

    suspend fun get(id: Long) = dao.get(id)

    suspend fun setActive(id: Long) = settings.setActivePlaylist(id)

    /** Saves a new or edited playlist and starts a refresh. */
    suspend fun save(playlist: PlaylistEntity): Long {
        val id = if (playlist.id == 0L) {
            dao.insert(playlist)
        } else {
            val existing = dao.get(playlist.id)
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
