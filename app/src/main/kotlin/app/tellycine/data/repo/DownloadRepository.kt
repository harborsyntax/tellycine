package app.tellycine.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import app.tellycine.core.ContentKind
import app.tellycine.data.db.DownloadEntity
import app.tellycine.data.db.DownloadStatus
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.net.DEFAULT_USER_AGENT
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What to download: a movie or an episode, with the headers its stream needs. */
data class DownloadRequest(
    val playlistId: Long,
    val kind: ContentKind,
    val itemKey: String,
    val streamId: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val url: String,
    val userAgent: String?,
    val referrer: String?,
)

/**
 * Saves movies and episodes to app storage with Android's [DownloadManager], which keeps going in
 * the background, survives restarts and shows its own progress notification. Files live in the
 * app's external files directory, so they need no storage permission and go when the app is removed.
 */
class DownloadRepository(
    private val context: Context,
    db: TellycineDatabase,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.downloads()
    private val system: DownloadManager? = context.getSystemService(DownloadManager::class.java)

    val all: Flow<List<DownloadEntity>> = dao.observeAll()

    /** Completed downloads, cached so playback can pick the local copy synchronously. */
    private val completed: StateFlow<Map<String, DownloadEntity>> = dao.observeAll()
        .map { list -> list.filter { it.status == DownloadStatus.COMPLETED }.associateBy { key(it.playlistId, it.itemKey) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private var poller: Job? = null

    init {
        // Catch up on anything that progressed or finished while the app wasn't running.
        ensurePolling()
    }

    fun observe(playlistId: Long, itemKeys: List<String>): Flow<Map<String, DownloadEntity>> =
        dao.observe(playlistId, itemKeys).map { list -> list.associateBy { it.itemKey } }

    /** The downloaded file for an item, if it finished and is still on the device. */
    fun localFile(playlistId: Long, itemKey: String): File? =
        completed.value[key(playlistId, itemKey)]?.let { File(it.filePath) }?.takeIf { it.isFile && it.length() > 0 }

    /** Starts (or restarts a failed) download. Fails with a readable message when it can't. */
    suspend fun start(request: DownloadRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val manager = system ?: return@withContext Result.failure(IllegalStateException("Downloads aren't available on this device."))
        if (!isDownloadable(request.url)) {
            return@withContext Result.failure(
                IllegalArgumentException("This title is a live-style stream (HLS or DASH) and can't be downloaded.")
            )
        }
        val existing = dao.get(request.playlistId, request.itemKey)
        if (existing != null && existing.status != DownloadStatus.FAILED && fileOk(existing)) return@withContext Result.success(Unit)
        existing?.let { discard(it) }

        val name = fileName(request)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: return@withContext Result.failure(IllegalStateException("Storage isn't available right now."))
        val file = File(dir, "$SUBDIR/$name")
        file.parentFile?.mkdirs()
        file.delete()

        val wifiOnly = settings.current().downloadWifiOnly
        val id = try {
            manager.enqueue(
                DownloadManager.Request(Uri.parse(request.url))
                    .addRequestHeader("User-Agent", request.userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT)
                    .apply { request.referrer?.ifBlank { null }?.let { addRequestHeader("Referer", it) } }
                    .setTitle(listOfNotNull(request.title, request.subtitle).joinToString(" · "))
                    .setDescription("Tellycine")
                    .setMimeType(mimeTypeFor(request.url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "$SUBDIR/$name")
                    .setAllowedOverMetered(!wifiOnly)
                    .setAllowedOverRoaming(!wifiOnly)
            )
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("Couldn't start the download: ${e.message}", e))
        }
        dao.upsert(
            DownloadEntity(
                playlistId = request.playlistId,
                itemKey = request.itemKey,
                kind = request.kind,
                streamId = request.streamId,
                title = request.title,
                subtitle = request.subtitle,
                artwork = request.artwork,
                sourceUrl = request.url,
                userAgent = request.userAgent,
                referrer = request.referrer,
                filePath = file.absolutePath,
                systemId = id,
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0,
                bytesTotal = -1,
                error = null,
                createdAt = System.currentTimeMillis(),
                completedAt = null,
            )
        )
        ensurePolling()
        Result.success(Unit)
    }

    /** Downloads [download] again from its original stream, e.g. after a failure or a missing file. */
    suspend fun retry(download: DownloadEntity): Result<Unit> = start(download.toRequest())

    /** Cancels an unfinished download or deletes a finished one, file included. */
    suspend fun remove(download: DownloadEntity) = withContext(Dispatchers.IO) {
        discard(download)
        dao.delete(download.playlistId, download.itemKey)
    }

    suspend fun removeAll() = withContext(Dispatchers.IO) {
        dao.all().forEach { remove(it) }
    }

    /** Bytes free on the storage that holds downloads. */
    fun freeBytes(): Long = runCatching {
        StatFs((context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).path).availableBytes
    }.getOrDefault(0L)

    fun fileOk(download: DownloadEntity): Boolean = File(download.filePath).let { it.isFile && it.length() > 0 }

    @Synchronized
    fun ensurePolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (refresh()) delay(POLL_MS)
        }
    }

    /** Copies progress from the system download manager. Returns whether anything is still downloading. */
    private suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val pending = dao.active()
        val manager = system
        if (pending.isEmpty() || manager == null) return@withContext false
        val states = HashMap<Long, SystemState>()
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(*pending.map { it.systemId }.toLongArray()))?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val reasonCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    states[c.getLong(idCol)] = SystemState(c.getInt(statusCol), c.getInt(reasonCol), c.getLong(soFarCol), c.getLong(totalCol))
                }
            }
        }
        var active = false
        for (d in pending) {
            val s = states[d.systemId]
            val updated = when (s?.status) {
                // Removed outside the app, e.g. from the notification or system settings.
                null -> d.copy(status = DownloadStatus.FAILED, error = "The download was cancelled.")
                DownloadManager.STATUS_SUCCESSFUL -> d.copy(
                    status = DownloadStatus.COMPLETED,
                    bytesDownloaded = s.soFar,
                    bytesTotal = if (s.total > 0) s.total else s.soFar,
                    error = null,
                    completedAt = System.currentTimeMillis(),
                )
                DownloadManager.STATUS_FAILED -> d.copy(status = DownloadStatus.FAILED, error = failureReason(s.reason))
                DownloadManager.STATUS_PAUSED -> d.copy(
                    status = DownloadStatus.PAUSED, bytesDownloaded = s.soFar, bytesTotal = s.total, error = pausedReason(s.reason),
                )
                DownloadManager.STATUS_RUNNING -> d.copy(
                    status = DownloadStatus.RUNNING, bytesDownloaded = s.soFar, bytesTotal = s.total, error = null,
                )
                else -> d.copy(status = DownloadStatus.QUEUED, error = null)
            }
            if (updated != d) dao.upsert(updated)
            if (updated.status.isActive) active = true
        }
        active
    }

    private fun discard(download: DownloadEntity) {
        // Removing from the system download manager also deletes its file; delete ours too in case it doesn't.
        runCatching { system?.remove(download.systemId) }
        runCatching { File(download.filePath).delete() }
    }

    private data class SystemState(val status: Int, val reason: Int, val soFar: Long, val total: Long)

    companion object {
        private const val SUBDIR = "downloads"
        private const val POLL_MS = 1_000L

        private fun key(playlistId: Long, itemKey: String) = "$playlistId/$itemKey"

        /** Only plain files can be saved; HLS and DASH are playlists of segments. */
        fun isDownloadable(url: String): Boolean {
            val uri = Uri.parse(url)
            val path = uri.path?.lowercase().orEmpty()
            return uri.scheme in setOf("http", "https") &&
                !path.endsWith(".m3u8") && !path.endsWith(".m3u") && !path.endsWith(".mpd")
        }

        private fun fileName(r: DownloadRequest): String {
            val ext = Uri.parse(r.url).path?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase()
                ?.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp4"
            val base = listOfNotNull(r.title, r.subtitle).joinToString(" - ")
                .replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(80).ifBlank { "video" }
            // The key keeps names unique (two episodes can share a title).
            val unique = Integer.toHexString("${r.playlistId}/${r.itemKey}".hashCode())
            return "$base-$unique.$ext"
        }

        private fun mimeTypeFor(url: String): String = when (Uri.parse(url).path?.substringAfterLast('.')?.lowercase()) {
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }

        private fun failureReason(reason: Int): String = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space."
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage isn't available."
            DownloadManager.ERROR_FILE_ERROR, DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Couldn't save the file."
            DownloadManager.ERROR_CANNOT_RESUME -> "The server doesn't allow resuming this download."
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects."
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "The connection was interrupted."
            in 400..599 -> "The server returned HTTP $reason."
            else -> "The download failed."
        }

        private fun pausedReason(reason: Int): String = when (reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a connection"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Retrying soon"
            else -> "Paused"
        }
    }
}

fun DownloadEntity.toRequest() = DownloadRequest(
    playlistId = playlistId,
    kind = kind,
    itemKey = itemKey,
    streamId = streamId,
    title = title,
    subtitle = subtitle,
    artwork = artwork,
    url = sourceUrl,
    userAgent = userAgent,
    referrer = referrer,
)
