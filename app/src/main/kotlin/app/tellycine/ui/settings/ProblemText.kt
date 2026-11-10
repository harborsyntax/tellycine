package app.tellycine.ui.settings

import android.text.format.DateUtils
import app.tellycine.data.ProblemLog

/** Settings' words for the last time the app stopped unexpectedly. */
object ProblemText {
    /** "5 minutes ago · IllegalArgumentException: …". */
    fun whenAndWhat(problem: ProblemLog.Problem, now: Long = System.currentTimeMillis()): String = "${ago(problem, now)} · ${problem.headline}"

    /** "5 minutes ago", "yesterday". */
    fun ago(problem: ProblemLog.Problem, now: Long = System.currentTimeMillis()): String =
        DateUtils.getRelativeTimeSpanString(problem.atMs, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

    const val PASS_ON_TV = "Scan the code with your phone's camera to copy it, or photograph it here, and send it with what you were " +
        "doing at the time. Links and server names are left out, and it stays on this TV unless you pass it on."

    const val PASS_ON_PHONE = "Share it, with what you were doing at the time. It says which version and device, and where in " +
        "Tellycine: links and server names are left out. It stays on this phone unless you share it."
}
