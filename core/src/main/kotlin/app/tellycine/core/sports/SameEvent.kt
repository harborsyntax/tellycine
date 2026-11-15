package app.tellycine.core.sports

import kotlin.math.max
import kotlin.math.min

/**
 * Whether names from different guides, languages and spellings are the same team, player or
 * event: "Bills" and "Buffalo Bills", "Bills de Buffalo" and "Buffalo Bills", "L Orient" and
 * "Leyton Orient", "Bayern München" and "Bayern Munich". Not "Manchester United" and "Manchester
 * City", nor "Arsenal" and "Arsenal Women".
 *
 * Only for names already on at about the same time: the Sports Hub asks about both sides of
 * matches starting close together, never about one name alone.
 */
object SameEvent {
    /** Whether [a] and [b] are the same side of a [sport] match. */
    fun sameSide(a: String, b: String, sport: Sport): Boolean {
        val ka = SportNames.key(a)
        val kb = SportNames.key(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val ta = tokens(ka)
        val tb = tokens(kb)
        // Women's and youth sides are teams of their own (though following one follows both).
        if (ta.any { it in WOMEN } != tb.any { it in WOMEN }) return false
        if (ta.filter { YOUTH.matches(it) }.toSet() != tb.filter { YOUTH.matches(it) }.toSet()) return false
        // Teams and players Tellycine knows are themselves by any of their names, and no one else.
        val fa = SportCatalogue.find(a, sport) ?: SportCatalogue.find(a)
        val fb = SportCatalogue.find(b, sport) ?: SportCatalogue.find(b)
        if (fa != null && fb != null) return fa == fb
        val plainA = ta.filter { it !in WOMEN && !YOUTH.matches(it) }
        val plainB = tb.filter { it !in WOMEN && !YOUTH.matches(it) }
        return if (plainA.size <= plainB.size) within(plainA, plainB) else within(plainB, plainA)
    }

    /** Whether "A v B" and "C v D" are the same match, either way round. */
    fun sameSides(a: Pair<String, String>, b: Pair<String, String>, sport: Sport): Boolean =
        (sameSide(a.first, b.first, sport) && sameSide(a.second, b.second, sport)) ||
            (sameSide(a.first, b.second, sport) && sameSide(a.second, b.first, sport))

    /**
     * Whether two events without sides are the same by name: "UK Championship" and "Snooker UK
     * Championship", "Grand Slam of Darts" and "Grand Slam Darts". The shorter name needs two
     * words of its own, so "Golf" alone isn't every golf event.
     */
    fun sameTitle(a: String, b: String): Boolean {
        val ka = SportNames.key(a)
        val kb = SportNames.key(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val ta = tokens(ka).filter { it !in TITLE_NOISE }
        val tb = tokens(kb).filter { it !in TITLE_NOISE }
        val (short, long) = if (ta.size <= tb.size) ta to tb else tb to ta
        return short.size >= 2 && within(short, long)
    }

    /**
     * Whether every word of [short] is one of [long]'s (the same word, its initial, the start of
     * it, or near enough in spelling), each a different one, with at least one the very same word
     * of three letters or more: "Bayern München" is "Bayern Munich", but "Austria" isn't
     * "Australia".
     */
    private fun within(short: List<String>, long: List<String>): Boolean {
        if (short.isEmpty()) return false
        val used = BooleanArray(long.size)
        var same = false
        for (word in short) {
            var found = -1
            var best = Match.NONE
            for ((i, other) in long.withIndex()) {
                if (used[i]) continue
                val match = wordMatch(word, other)
                // The very same word is better than a near one, an initial or a start further on.
                if (match.ordinal > best.ordinal) {
                    found = i
                    best = match
                }
                if (best == Match.SAME) break
            }
            if (found < 0) return false
            used[found] = true
            if (best == Match.SAME && word.length >= 3) same = true
        }
        return same
    }

    private enum class Match { NONE, PART, NEAR, SAME }

    private fun wordMatch(a: String, b: String): Match {
        if (a == b) return Match.SAME
        // "L" for Leyton, "Man" for Manchester, "Sheff" for Sheffield.
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (short.length == 1 && short.all { it.isLetter() }) return if (long.startsWith(short)) Match.PART else Match.NONE
        if (short.length >= 3 && short.all { it.isLetter() } && long.startsWith(short)) return Match.PART
        // München and Munich, Sevilla and Seville: near enough, for words long enough to tell.
        if (min(a.length, b.length) >= 4 && jaroWinkler(a, b) >= 0.88) return Match.NEAR
        return Match.NONE
    }

    /**
     * How alike two names look, from 0 to 1, for pairing one event's sides with another's: without
     * the words that only join a name ("Les Bills" looks like "Bills").
     */
    fun similarity(a: String, b: String): Double =
        jaroWinkler(tokens(SportNames.key(a)).joinToString(" "), tokens(SportNames.key(b)).joinToString(" "))

    /** [key]'s words, less those that only join or describe a name: "de", "the", "fc". */
    private fun tokens(key: String): List<String> = key.split(' ').filter { it.isNotEmpty() && it !in JOINING }

    private val JOINING = setOf(
        "de", "du", "des", "la", "le", "les", "el", "los", "las", "the", "of", "and", "y", "e", "et", "do", "da", "dos", "das",
        "der", "die", "den", "van", "von", "di", "del", "della", "fc", "cf", "sc", "ac", "afc", "cd", "sv", "fk", "club",
    )
    private val WOMEN = setOf("women", "womens", "ladies", "femenino", "feminino", "feminine", "feminin", "frauen", "w", "wfc")
    private val YOUTH = Regex("u\\d{2}")

    /** Words in events' names that don't tell one from another. */
    private val TITLE_NOISE = setOf("live", "snooker", "darts", "golf", "tennis", "boxing", "football", "coverage", "day", "session")

    /** Jaro-Winkler similarity of [a] and [b], from 0 (nothing alike) to 1 (the same). */
    internal fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = max(0, max(a.length, b.length) / 2 - 1)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val from = max(0, i - range)
            val to = min(b.length - 1, i + range)
            for (j in from..to) {
                if (bMatched[j] || a[i] != b[j]) continue
                aMatched[i] = true
                bMatched[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3
        var prefix = 0
        while (prefix < min(4, min(a.length, b.length)) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }
}
