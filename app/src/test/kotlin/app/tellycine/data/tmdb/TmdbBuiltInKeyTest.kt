package app.tellycine.data.tmdb

import app.tellycine.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tellycine's own TMDB key goes into the app scrambled, and comes back out as it went in. */
class TmdbBuiltInKeyTest {
    @Test
    fun comesBackAsBuiltIn() {
        // The build's TMDB_API_KEY, if it had one (tests run with the build's environment).
        assertEquals(System.getenv("TMDB_API_KEY").orEmpty().trim(), TmdbBuiltInKey.value)
        assertEquals(BuildConfig.TMDB_KEY_DATA.size, BuildConfig.TMDB_KEY_MASK.size)
    }

    @Test
    fun scrambledItIsntTheKey() {
        val key = "0123456789abcdef"
        val mask = IntArray(key.length) { (it * 37 + 11) and 0xFF }
        val data = IntArray(key.length) { key[it].code xor mask[it] }
        assertTrue(data.map { it.toChar() }.joinToString("") != key)
        assertEquals(key, TmdbBuiltInKey.unscramble(data, mask))
    }
}
