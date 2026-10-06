package app.tube.player

import android.content.Context
import android.content.Intent
import app.tube.core.ContentKind

/** Everything the player needs to start playback and record watch history. */
data class PlayRequest(
    val playlistId: Long,
    val kind: ContentKind,
    val streamId: String,
    val historyKey: String,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val artwork: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val startPositionMs: Long = 0,
    /** Category the channel was opened from, used for channel up/down in the player. */
    val categoryId: String? = null,
    /** Replay of a past programme from the provider's archive (seekable, not tracked in history). */
    val catchup: Boolean = false,
) {
    val isLive get() = kind == ContentKind.LIVE && !catchup

    fun toIntent(context: Context): Intent = Intent(context, PlayerActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(EXTRA_PLAYLIST, playlistId)
        .putExtra(EXTRA_KIND, kind.name)
        .putExtra(EXTRA_STREAM, streamId)
        .putExtra(EXTRA_HISTORY_KEY, historyKey)
        .putExtra(EXTRA_URL, url)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_SUBTITLE, subtitle)
        .putExtra(EXTRA_ARTWORK, artwork)
        .putExtra(EXTRA_USER_AGENT, userAgent)
        .putExtra(EXTRA_REFERRER, referrer)
        .putExtra(EXTRA_START, startPositionMs)
        .putExtra(EXTRA_CATEGORY, categoryId)
        .putExtra(EXTRA_CATCHUP, catchup)

    companion object {
        private const val EXTRA_PLAYLIST = "playlist"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_STREAM = "stream"
        private const val EXTRA_HISTORY_KEY = "history_key"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_USER_AGENT = "user_agent"
        private const val EXTRA_REFERRER = "referrer"
        private const val EXTRA_START = "start"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_CATCHUP = "catchup"

        fun fromIntent(intent: Intent): PlayRequest? {
            val url = intent.getStringExtra(EXTRA_URL) ?: return null
            return PlayRequest(
                playlistId = intent.getLongExtra(EXTRA_PLAYLIST, 0),
                kind = runCatching { ContentKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!) }.getOrDefault(ContentKind.LIVE),
                streamId = intent.getStringExtra(EXTRA_STREAM).orEmpty(),
                historyKey = intent.getStringExtra(EXTRA_HISTORY_KEY).orEmpty(),
                url = url,
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
                artwork = intent.getStringExtra(EXTRA_ARTWORK),
                userAgent = intent.getStringExtra(EXTRA_USER_AGENT),
                referrer = intent.getStringExtra(EXTRA_REFERRER),
                startPositionMs = intent.getLongExtra(EXTRA_START, 0),
                categoryId = intent.getStringExtra(EXTRA_CATEGORY),
                catchup = intent.getBooleanExtra(EXTRA_CATCHUP, false),
            )
        }
    }
}

fun Context.play(request: PlayRequest) = startActivity(request.toIntent(this))
