package app.tellycine.data.sync

import android.util.Log
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.SyncState
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SyncProgress(val message: String, val fraction: Float?)

/**
 * Runs playlist refreshes in the application scope, one at a time, and exposes live
 * progress for the UI. [onContentSynced] runs after each playlist's content is refreshed, and
 * [onRefreshed] after its content or guide changed, with its id and whether its content did.
 */
class SyncManager(
    private val db: TellycineDatabase,
    private val syncer: PlaylistSyncer,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val onContentSynced: () -> Unit = {},
    private val onRefreshed: (playlistId: Long, contentChanged: Boolean) -> Unit = { _, _ -> },
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
     * Refreshes playlists whose content is older than a day, and guides that are running out
     * ([guideDue]). They start a little after launch, so the first screen opens without a download
     * and import competing with it, and the playlist in use goes first. (They run one at a time.)
     */
    fun refreshStale() {
        scope.launch {
            dao.resetInterruptedSyncs()
            val prefs = settings.current()
            if (!prefs.refreshOnLaunch) return@launch
            val stale = dao.getAll().filter { due(it, prefs.epgDays) != null }.sortedByDescending { it.id == prefs.activePlaylistId }
            if (stale.isEmpty()) return@launch
            delay(LAUNCH_DELAY)
            for (listed in stale) {
                // Unless it was refreshed (or removed) in the meantime.
                val p = dao.get(listed.id) ?: continue
                val (content, guide) = due(p, prefs.epgDays) ?: continue
                sync(p.id, content = content, epg = guide)
            }
        }
    }

    /** What of [p] needs refreshing, its content and its guide, or null if neither does. */
    private fun due(p: PlaylistEntity, guideDays: Int): Pair<Boolean, Boolean>? {
        val now = System.currentTimeMillis()
        val content = p.lastSyncedAt == null || now - p.lastSyncedAt > DAY
        val guide = syncer.epgUrlsFor(p).isNotEmpty() && guideDue(p, guideDays, now)
        return if (content || guide) content to guide else null
    }

    private suspend fun run(playlistId: Long, content: Boolean, epg: Boolean) {
        var playlist: PlaylistEntity = dao.get(playlistId) ?: return
        val prefs = settings.current()
        val report: ProgressReporter = { message, fraction -> setProgress(playlistId, SyncProgress(message, fraction)) }

        var guide = epg
        var changed = false
        var contentChanged = false
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
            changed = true
            contentChanged = true
            onContentSynced()
            // Its guide needs importing again for channels it didn't have before, even if it isn't due.
            guide = guide || withContext(Dispatchers.IO) { syncer.guideInputs(playlist, prefs.epgDays) } != playlist.epgInputs
        }

        var epgError: String? = null
        if (guide && syncer.epgUrlsFor(playlist).isNotEmpty()) {
            try {
                if (syncer.syncEpg(playlist, prefs.epgDays, report) is GuideRefresh.Imported) changed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "EPG sync failed for $playlistId", e)
                epgError = "TV guide: ${e.message ?: "update failed"}"
            }
        }
        dao.setSyncState(playlistId, if (epgError != null) SyncState.ERROR else SyncState.IDLE, epgError)
        if (changed) onRefreshed(playlistId, contentChanged)
    }

    private fun setProgress(id: Long, progress: SyncProgress) {
        _progress.update { it + (id to progress) }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val DAY = 24 * 60 * 60 * 1000L

        /**
         * Whether [p]'s TV guide is due to be refreshed as the app starts, when [days] of guide are
         * kept: when it's running out (less than a day left, or half the days kept when that's
         * less), or two days after it was last refreshed, for changes to the schedule; but no more
         * than every 12 hours. Without what's left (a guide imported by an older version), every 12
         * hours.
         */
        fun guideDue(p: PlaylistEntity, days: Int, now: Long): Boolean {
            val refreshed = p.epgCheckedAt ?: p.epgSyncedAt ?: return true
            val age = now - refreshed
            if (age < DAY / 2) return false
            if (age >= 2 * DAY) return true
            val coveredUntil = p.epgCoveredUntil ?: return true
            return coveredUntil - now < minOf(DAY, days * DAY / 2)
        }

        /** How long after launch stale playlists start refreshing. */
        private const val LAUNCH_DELAY = 10_000L
    }
}
