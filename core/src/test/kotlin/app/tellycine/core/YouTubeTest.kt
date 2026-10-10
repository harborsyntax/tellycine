package app.tellycine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {
    @Test
    fun acceptsBareIdsAndEveryLinkStyle() {
        val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        listOf(
            "dQw4w9WgXcQ",
            " dQw4w9WgXcQ ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
        ).forEach { assertEquals(it, watch, YouTube.trailerUrl(it)) }
    }

    @Test
    fun ignoresAnythingElse() {
        listOf(null, "", "  ", "null", "trailer", "dQw4w9WgXc", "dQw4w9WgXcQQ", "https://vimeo.com/123456789", "https://youtu.be/dQw4w9WgXcQQ")
            .forEach { assertNull(it, YouTube.trailerUrl(it)) }
    }
}
