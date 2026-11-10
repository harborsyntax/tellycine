package app.tellycine.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.tellycine.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last time Tellycine stopped unexpectedly: an error it couldn't handle, or (from Android 11)
 * Android closing it while it was on screen, to free memory or because it stopped responding.
 *
 * It's kept on the device only, for Settings to show and the person to pass on if they choose
 * (on a TV, by a QR code their phone can copy). Links, addresses and server names are left out,
 * as they can include logins.
 */
class ProblemLog(private val context: Context) {
    data class Problem(val atMs: Long, val headline: String, val report: String)

    private val _last = MutableStateFlow<Problem?>(null)

    /** The last problem kept, until it's forgotten. */
    val last: StateFlow<Problem?> = _last.asStateFlow()

    /** Reads what was kept, after noting how Android says the app last stopped. */
    suspend fun load() = withContext(Dispatchers.IO) {
        runCatching { recordExit() }
        _last.value = runCatching { read(context) }.getOrNull()
    }

    fun forget() {
        file(context).delete()
        _last.value = null
    }

    /** Android's own account of the last time the app stopped, when it closed it while on screen. */
    private fun recordExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val exit = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (exit.timestamp <= prefs.getLong(EXIT_SEEN, 0)) return
        prefs.edit().putLong(EXIT_SEEN, exit.timestamp).apply()
        // In the background, Android closes apps all the time; only on screen is it a problem.
        if (exit.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) return
        val headline = when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Android closed Tellycine to free memory"
            ApplicationExitInfo.REASON_ANR -> "Tellycine stopped responding, and Android closed it"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Tellycine stopped in system or video code"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Android closed Tellycine for using too much"
            ApplicationExitInfo.REASON_SIGNALED -> "Android closed Tellycine (signal ${exit.status})"
            // Its own errors are kept as they happen, with more to go on.
            else -> return
        }
        // An error kept as it happened says more than Android's account of the same moment.
        read(context)?.let { if (it.atMs >= exit.timestamp - 10_000) return }
        val detail = listOfNotNull(
            exit.description?.let(::redact),
            "Memory in use: ${exit.pss / 1024} MB",
        ).joinToString("\n")
        write(context, exit.timestamp, headline, "$headline\n${about(exit.timestamp)}\n$detail")
    }

    companion object {
        private const val FILE = "last-problem.txt"
        private const val PREFS = "problems"
        private const val EXIT_SEEN = "exitSeen"

        /** Small enough for a QR code a phone can read from across the room. Plain ASCII where it can be. */
        private const val MAX_REPORT = 1_800

        /** Keeps the error that's about to stop the app, then lets Android's own handler close it. */
        fun install(context: Context) {
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching {
                    val now = System.currentTimeMillis()
                    write(app, now, headline(error), report(now, thread, error))
                }
                previous?.uncaughtException(thread, error)
            }
        }

        private fun file(context: Context) = File(context.filesDir, FILE)

        private fun write(context: Context, atMs: Long, headline: String, report: String) {
            file(context).writeText("$atMs\n${headline.replace('\n', ' ')}\n${report.take(MAX_REPORT)}")
        }

        private fun read(context: Context): Problem? {
            val f = file(context)
            if (!f.exists()) return null
            val lines = f.readText().split('\n', limit = 3)
            if (lines.size < 3) return null
            return Problem(lines[0].toLongOrNull() ?: return null, lines[1], lines[2])
        }

        /** "IllegalArgumentException: Key "x" was already used", without its package. */
        fun headline(error: Throwable): String {
            val root = generateSequence(error) { it.cause }.last()
            return "${root.javaClass.simpleName}: ${redact(root.message.orEmpty())}".trimEnd(':', ' ')
        }

        /**
         * What happened and where: the version, the device, and each error in the chain with where
         * it was thrown and Tellycine's own code it came through.
         */
        fun report(atMs: Long, thread: Thread, error: Throwable): String = buildString {
            appendLine(about(atMs) + " | thread ${thread.name}")
            var e: Throwable? = error
            var first = true
            while (e != null) {
                appendLine((if (first) "" else "Caused by: ") + "${e.javaClass.name}: ${redact(e.message.orEmpty())}")
                val frames = e.stackTrace
                // Where it was thrown, then the way through Tellycine's code.
                val shown = (frames.take(4) + frames.drop(4).filter { it.className.startsWith("app.tellycine") }.take(8)).distinct()
                for (f in shown) appendLine("  at ${f.className.substringAfterLast('.')}.${f.methodName}(${f.fileName}:${f.lineNumber})")
                first = false
                e = e.cause?.takeIf { it !== e }
            }
        }

        private fun about(atMs: Long): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(atMs))
            return "Tellycine ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} | $time"
        }

        private val LINK = Regex("(?i)\\b[a-z][a-z0-9+.-]*://\\S+")
        private val ADDRESS = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b")
        private val QUOTED_HOST = Regex("\"[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+\"")

        /** [text] without links, addresses or quoted server names, which can carry logins. */
        fun redact(text: String): String =
            text.replace(LINK, "<link>").replace(ADDRESS, "<address>").replace(QUOTED_HOST, "\"<server>\"")
    }
}
