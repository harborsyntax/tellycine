package app.tellycine.data.sync

import android.util.Log
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.SyncState
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

data class SyncProgress(val message: String, val fraction: Float?)

/**
 * Runs playlist refreshes in the application scope, one at a time, and exposes live
 * progress for the UI.
 */
class SyncManager(
    private val db: TellycineDatabase,
    private val syncer: PlaylistSyncer,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.playlists()
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val permits = Semaphore(1)

    private val _progress = MutableStateFlow<Map<Long, SyncProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, SyncProgress>> = _progress.asStateFlow()

    fun isSyncing(playlistId: Long) = jobs[playlistId]?.isActive == true

    /** Refreshes the playlist's content and/or TV guide. No-op if already running. */
    fun sync(playlistId: Long, content: Boolean = true, epg: Boolean = true) {
        if (isSyncing(playlistId)) return
        val job = scope.launch {
            setProgress(playlistId, SyncProgress("Waiting…", null))
            dao.setSyncState(playlistId, SyncState.SYNCING, null)
            try {
                permits.withPermit { run(playlistId, content, epg) }
            } catch (e: CancellationException) {
                dao.setSyncState(playlistId, SyncState.IDLE, null)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed for $playlistId", e)
                dao.setSyncState(playlistId, SyncState.ERROR, e.message ?: e.javaClass.simpleName)
            } finally {
                _progress.update { it - playlistId }
                jobs.remove(playlistId)
            }
        }
        jobs[playlistId] = job
    }

    fun cancel(playlistId: Long) {
        jobs[playlistId]?.cancel()
    }

    /**
     * Refreshes playlists whose content is older than a day and guides older than 12 hours. They
     * start a little after launch, so the first screen opens without a download and import
     * competing with it, and the playlist in use goes first. (They run one at a time.)
     */
    fun refreshStale() {
        scope.launch {
            dao.resetInterruptedSyncs()
            val prefs = settings.current()
            if (!prefs.refreshOnLaunch) return@launch
            val stale = dao.getAll().filter { isStale(it) != null }.sortedByDescending { it.id == prefs.activePlaylistId }
            if (stale.isEmpty()) return@launch
            delay(LAUNCH_DELAY)
            for (listed in stale) {
                // Unless it was refreshed (or removed) in the meantime.
                val p = dao.get(listed.id) ?: continue
                val contentStale = isStale(p) ?: continue
                sync(p.id, content = contentStale, epg = true)
            }
        }
    }

    /** Whether [p] needs refreshing: null if not, else whether its content does (or only its guide). */
    private fun isStale(p: PlaylistEntity): Boolean? {
        val now = System.currentTimeMillis()
        val contentStale = p.lastSyncedAt == null || now - p.lastSyncedAt > DAY
        val epgStale = syncer.epgUrlsFor(p).isNotEmpty() && (p.epgSyncedAt == null || now - p.epgSyncedAt > DAY / 2)
        return if (contentStale || epgStale) contentStale else null
    }

    private suspend fun run(playlistId: Long, content: Boolean, epg: Boolean) {
        var playlist: PlaylistEntity = dao.get(playlistId) ?: return
        val prefs = settings.current()
        val report: ProgressReporter = { message, fraction -> setProgress(playlistId, SyncProgress(message, fraction)) }

        if (content || playlist.lastSyncedAt == null) {
            val result = syncer.syncContent(playlist, prefs.preferHls, report)
            dao.setContentSynced(
                id = playlistId,
                live = result.live,
                movies = result.movies,
                series = result.series,
                at = System.currentTimeMillis(),
                detectedEpg = result.detectedEpgUrl,
                // Dispatcharr reports a synthetic "now + 90 days" expiry; don't show it.
                expiresAt = result.account?.takeUnless { it.isDispatcharr }?.expiresAtMs,
                maxConnections = result.account?.maxConnections,
                serverTimezone = result.account?.serverTimezone,
                serverSoftware = result.account?.serverSoftware,
            )
            playlist = dao.get(playlistId) ?: return
        }

        var epgError: String? = null
        if (epg && syncer.epgUrlsFor(playlist).isNotEmpty()) {
            try {
                val count = syncer.syncEpg(playlist, prefs.epgDays, report)
                dao.setEpgSynced(playlistId, System.currentTimeMillis(), count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "EPG sync failed for $playlistId", e)
                epgError = "TV guide: ${e.message ?: "update failed"}"
            }
        }
        dao.setSyncState(playlistId, if (epgError != null) SyncState.ERROR else SyncState.IDLE, epgError)
    }

    private fun setProgress(id: Long, progress: SyncProgress) {
        _progress.update { it + (id to progress) }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val DAY = 24 * 60 * 60 * 1000L

        /** How long after launch stale playlists start refreshing. */
        private const val LAUNCH_DELAY = 10_000L
    }
}
