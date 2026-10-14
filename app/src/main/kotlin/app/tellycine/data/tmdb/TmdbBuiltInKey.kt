package app.tellycine.data.tmdb

import app.tellycine.BuildConfig

/**
 * Tellycine's own TMDB key, used unless people add theirs; empty in builds without one (they ask for
 * a key of the user's own). It's built in scrambled (see app/build.gradle.kts) and only put back
 * together here, so it isn't a readable string in the app. That keeps it from casual searches, not
 * from anyone determined, which is why people can always use their own key instead.
 */
object TmdbBuiltInKey {
    val value: String by lazy { unscramble(BuildConfig.TMDB_KEY_DATA, BuildConfig.TMDB_KEY_MASK) }

    internal fun unscramble(data: IntArray, mask: IntArray): String =
        String(CharArray(data.size) { (data[it] xor mask[it]).toChar() })
}
