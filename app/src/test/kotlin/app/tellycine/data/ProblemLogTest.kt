package app.tellycine.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The last time the app stopped unexpectedly, kept for Settings to show. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProblemLogTest {
    @Test
    fun anErrorIsKeptWithoutItsLinksAndAndroidStillHandlesIt() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var handled: Throwable? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, e -> handled = e }
            ProblemLog.install(app)
            val error = IllegalStateException(
                "Couldn't load http://user:secret@example.com:8080/get.php?username=user from 10.0.0.2:8080",
                IllegalArgumentException("Key \"Arsenal\" was already used. Unable to resolve host \"example.com\""),
            )
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertSame(error, handled)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }

        val log = ProblemLog(app)
        runBlocking { log.load() }
        val problem = log.last.value!!
        assertEquals("IllegalArgumentException: Key \"Arsenal\" was already used. Unable to resolve host \"<server>\"", problem.headline)
        assertTrue(problem.report, problem.report.startsWith("Tellycine "))
        assertTrue(problem.report, problem.report.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(problem.report, problem.report.contains("at ProblemLogTest.anErrorIsKeptWithoutItsLinksAndAndroidStillHandlesIt"))
        // Nothing of the login or the server.
        for (secret in listOf("secret", "username=", "example.com", "10.0.0.2")) assertFalse(problem.report, problem.report.contains(secret))
        assertTrue(problem.report.length <= 1_800)

        log.forget()
        assertNull(log.last.value)
        runBlocking { log.load() }
        assertNull(log.last.value)
    }
}
