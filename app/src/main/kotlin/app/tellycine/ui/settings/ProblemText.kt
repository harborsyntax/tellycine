package app.tellycine.ui.settings

import android.text.format.DateUtils
import app.tellycine.data.ProblemLog

/** Settings' words for the last time the app stopped unexpectedly. */
object ProblemText {
    /** "Today, 21:40 · IllegalArgumentException: …". */
    fun whenAndWhat(problem: ProblemLog.Problem, now: Long = System.currentTimeMillis()): String {
        val time = DateUtils.getRelativeTimeSpanString(problem.atMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
        return "$time · ${problem.headline}"
    }

    const val PASS_ON_TV = "Scan it with your phone's camera to copy what went wrong, and send it with what you were doing at the " +
        "time. It says which version and device, and where in Tellycine: links and server names are left out. It stays on this TV."

    const val PASS_ON_PHONE = "Share it, with what you were doing at the time. It says which version and device, and where in " +
        "Tellycine: links and server names are left out. It stays on this phone unless you share it."
}
