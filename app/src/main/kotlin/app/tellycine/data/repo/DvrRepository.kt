package app.tellycine.data.repo

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import app.tellycine.core.dispatcharr.DispatcharrParser
import app.tellycine.core.dispatcharr.DispatcharrSource
import app.tellycine.core.dispatcharr.DvrRecording
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.ProgrammeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class DvrException(message: String) : IOException(message)

/** Recordings state for one playlist. */
data class DvrState(
    val recordings: List<DvrRecording> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long? = null,
)

/**
 * Dispatcharr DVR integration: sign-in (exchanging a username/password for the user's API
 * key), listing, scheduling and deleting recordings, and building authenticated playback URLs.
 */
class DvrRepository(private val db: TellycineDatabase, httpClient: OkHttpClient) {

    private val http = httpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private val _state = MutableStateFlow<Map<Long, DvrState>>(emptyMap())
    val state: StateFlow<Map<Long, DvrState>> = _state.asStateFlow()

    fun baseUrl(playlist: PlaylistEntity) = XtreamUrls.normaliseServer(playlist.url)

    // ---- Account ----

    /**
     * Signs in with Dispatcharr credentials and stores the user's API key. An existing key is
     * reused rather than regenerated, because regenerating would break other apps using it.
     */
    suspend fun connect(playlist: PlaylistEntity, username: String, password: String) = withContext(Dispatchers.IO) {
        val base = baseUrl(playlist)
        val tokenBody = com.google.gson.JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }.toString()
        val token = execute(Request.Builder().url("$base/api/accounts/token/").post(tokenBody.toRequestBody(json)).build(), login = true)
            .let { DispatcharrParser.parseAccessToken(it.reader()) }
            ?: throw DvrException("Dispatcharr didn't return a session token.")
        val bearer = "Bearer $token"
        val existing = execute(Request.Builder().url("$base/api/accounts/api-keys/").header("Authorization", bearer).build())
            .let { DispatcharrParser.parseApiKey(it.reader()) }
        val key = existing ?: execute(
            Request.Builder().url("$base/api/accounts/api-keys/generate/").header("Authorization", bearer)
                .post("{}".toRequestBody(json)).build()
        ).let { DispatcharrParser.parseApiKey(it.reader()) } ?: throw DvrException("Dispatcharr didn't return an API key.")
        verifyAndSave(playlist, key, username)
    }

    /** Uses an API key copied from Dispatcharr's settings. */
    suspend fun connectWithKey(playlist: PlaylistEntity, apiKey: String) = withContext(Dispatchers.IO) {
        verifyAndSave(playlist, apiKey.trim(), null)
    }

    private suspend fun verifyAndSave(playlist: PlaylistEntity, key: String, username: String?) {
        val recordings = fetchRecordings(baseUrl(playlist), key)
        db.playlists().setDvrCredentials(playlist.id, key, username)
        _state.update { it + (playlist.id to DvrState(recordings, loadedAt = System.currentTimeMillis())) }
    }

    suspend fun disconnect(playlistId: Long) {
        db.playlists().setDvrCredentials(playlistId, null, null)
        _state.update { it - playlistId }
    }

    // ---- Recordings ----

    suspend fun refresh(playlist: PlaylistEntity) {
        val key = playlist.dvrApiKey ?: return
        _state.update { it + (playlist.id to (it[playlist.id] ?: DvrState()).copy(loading = true, error = null)) }
        try {
            val list = withContext(Dispatchers.IO) { fetchRecordings(baseUrl(playlist), key) }
            _state.update { it + (playlist.id to DvrState(list, loadedAt = System.currentTimeMillis())) }
        } catch (e: IOException) {
            _state.update { it + (playlist.id to (it[playlist.id] ?: DvrState()).copy(loading = false, error = e.message ?: "Couldn't load recordings")) }
        }
    }

    /** Schedules [programme] on [channelId]; Dispatcharr applies its own pre/post padding. */
    suspend fun schedule(playlist: PlaylistEntity, channelId: Long, programme: ProgrammeEntity) {
        val key = playlist.dvrApiKey ?: throw DvrException("Connect to Dispatcharr first.")
        val body = DispatcharrParser.recordingRequest(channelId, programme.startMs, programme.stopMs, programme.title, programme.description)
        withContext(Dispatchers.IO) {
            execute(authed(Request.Builder().url("${baseUrl(playlist)}/api/channels/recordings/").post(body.toRequestBody(json)), key).build())
        }
        refresh(playlist)
    }

    /** Cancels a scheduled recording, or deletes a recorded one (and its file). */
    suspend fun delete(playlist: PlaylistEntity, recordingId: Long) {
        val key = playlist.dvrApiKey ?: return
        withContext(Dispatchers.IO) {
            execute(authed(Request.Builder().url("${baseUrl(playlist)}/api/channels/recordings/$recordingId/").delete(), key).build())
        }
        _state.update { map ->
            val current = map[playlist.id] ?: return@update map
            map + (playlist.id to current.copy(recordings = current.recordings.filterNot { it.id == recordingId }))
        }
    }

    /** Upstream streams behind a Dispatcharr channel (needs a Standard user or above). */
    suspend fun channelSources(playlist: PlaylistEntity, channelId: Long): List<DispatcharrSource> {
        val key = playlist.dvrApiKey ?: throw DvrException("Connect your Dispatcharr login in the playlist settings first.")
        return withContext(Dispatchers.IO) {
            DispatcharrParser.parseSources(
                execute(authed(Request.Builder().url("${baseUrl(playlist)}/api/channels/channels/$channelId/streams/"), key).build()).reader()
            )
        }
    }

    /** Output profile id found per playlist (null when there isn't one), with when it was looked up. */
    private val aacProfiles = ConcurrentHashMap<Long, Pair<Long?, Long>>()

    /**
     * Id of the Dispatcharr output profile that re-encodes audio to AAC (normally the built-in
     * "Web Player (AAC Audio)"), so Chromecast gets sound it can play. Needs the connected
     * login (a Standard user or above); null when it isn't connected, allowed or available.
     */
    suspend fun aacOutputProfile(playlist: PlaylistEntity): Long? {
        val key = playlist.dvrApiKey?.takeIf { playlist.isDispatcharr } ?: return null
        val now = System.currentTimeMillis()
        aacProfiles[playlist.id]?.let { (id, at) -> if (now - at < (if (id != null) 30 else 2) * 60_000L) return id }
        val id = try {
            withContext(Dispatchers.IO) {
                val body = execute(authed(Request.Builder().url("${baseUrl(playlist)}/api/core/outputprofiles/"), key).build())
                DispatcharrParser.aacProfile(DispatcharrParser.parseOutputProfiles(body.reader()))?.id
            }
        } catch (e: DvrException) {
            Log.w("DvrRepository", "No AAC output profile: ${e.message}")
            null
        }
        aacProfiles[playlist.id] = id to now
        return id
    }

    /** Range-seekable file, or Dispatcharr redirects to live HLS while still recording. Needs the X-API-Key header. */
    fun playbackUrl(playlist: PlaylistEntity, recordingId: Long) = "${baseUrl(playlist)}/api/channels/recordings/$recordingId/file/"

    fun posterUrl(playlist: PlaylistEntity, recording: DvrRecording): String? {
        val base = baseUrl(playlist)
        val poster = recording.posterUrl
        return when {
            poster != null && (poster.startsWith("http://") || poster.startsWith("https://")) -> poster
            poster != null && poster.startsWith("/") -> base + poster
            recording.posterLogoId != null -> "$base/api/channels/logos/${recording.posterLogoId}/cache/"
            else -> null
        }
    }

    private fun fetchRecordings(base: String, key: String): List<DvrRecording> =
        DispatcharrParser.parseRecordings(execute(authed(Request.Builder().url("$base/api/channels/recordings/"), key).build()).reader())

    private fun authed(builder: Request.Builder, key: String) = builder.header("X-API-Key", key)

    private fun execute(request: Request, login: Boolean = false): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw DvrException("Couldn't reach Dispatcharr: ${e.message}")
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (it.isSuccessful) return body
            val detail = DispatcharrParser.parseError(body)
            throw DvrException(
                when (it.code) {
                    401 -> if (login) "Wrong Dispatcharr username or password." else "Dispatcharr rejected the saved login. Reconnect in the playlist settings."
                    403 -> "Your Dispatcharr user doesn't have permission for this. Ask the admin to enable DVR access${detail?.let { d -> " ($d)" } ?: ""}."
                    404 -> "This Dispatcharr server doesn't support recordings."
                    else -> detail ?: "Dispatcharr returned HTTP ${it.code}."
                }
            )
        }
    }
}
