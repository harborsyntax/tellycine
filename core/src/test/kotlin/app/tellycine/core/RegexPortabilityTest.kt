package app.tellycine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's regular expressions (ICU) are stricter than the JVM's these tests run on: a bare "}",
 * a "{" that isn't a repeat count, or braces and "[" inside a character class, which the JVM takes
 * as plain characters, ICU refuses, and the app stops the first time it's used. Every pattern in
 * the app's code is checked here for them, as the tests themselves can't tell.
 */
class RegexPortabilityTest {
    private val sources = listOf(File("src/main"), File("../app/src/main")).filter { it.isDirectory }

    /** `Regex("…")` and `Regex("""…""")`, as written in the code. */
    private val literal = Regex("Regex\\(\\s*(?:\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\")", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun everyPatternIsOneAndroidAccepts() {
        assertTrue("The app's sources weren't found from ${File(".").absolutePath}", sources.size == 2)
        var checked = 0
        val problems = ArrayList<String>()
        for (dir in sources) {
            dir.walk().filter { it.extension == "kt" }.forEach { file ->
                val text = file.readText()
                for (m in literal.findAll(text)) {
                    val raw = m.groups[1]
                    val pattern = raw?.value ?: unescape(m.groupValues[2])
                    checked++
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    problemsIn(pattern).forEach { problems += "${file.path}:$line: $pattern: $it" }
                }
            }
        }
        assertTrue("Only $checked patterns found", checked > 50)
        assertEquals(problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun theCheckFindsWhatAndroidRefuses() {
        assertEquals(listOf("a \"}\" on its own at 17"), problemsIn("\\$?\\{([A-Za-z_]+)}"))
        assertEquals(listOf("a \"{\" that isn't a count at 0"), problemsIn("{utc"))
        assertEquals(listOf("\"{\" in a character class at 1"), problemsIn("[{]"))
        assertEquals(emptyList<String>(), problemsIn("\\$?\\{([A-Za-z_]+)\\}"))
        assertEquals(emptyList<String>(), problemsIn("[^\\p{L}\\p{N}]+\\d{2,3}x{4}"))
    }

    private fun problemsIn(p: String): List<String> {
        val found = ArrayList<String>()
        var i = 0
        var inClass = false
        while (i < p.length) {
            val c = p[i]
            when {
                c == '\\' -> {
                    // \p{L}, \x{41} and the like are one token.
                    i += if (i + 2 < p.length && p[i + 1] in "pPxN" && p[i + 2] == '{') p.indexOf('}', i) - i + 1 else 2
                    continue
                }
                inClass -> when (c) {
                    '[' -> found += "\"[\" in a character class at $i"
                    '{', '}' -> found += "\"$c\" in a character class at $i"
                    ']' -> inClass = false
                }
                c == '[' -> {
                    inClass = true
                    if (p.getOrNull(i + 1) == '^') i++
                    // A "]" first in a class is a plain one.
                    if (p.getOrNull(i + 1) == ']') i++
                }
                c == '{' -> {
                    val count = COUNT.find(p, i)?.takeIf { it.range.first == i }
                    if (count != null) {
                        i = count.range.last + 1
                        continue
                    }
                    found += "a \"{\" that isn't a count at $i"
                }
                c == '}' -> found += "a \"}\" on its own at $i"
            }
            i++
        }
        return found
    }

    /** What a Kotlin string's escapes stand for. */
    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    'u' -> {
                        append(s.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 6
                        continue
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private companion object {
        val COUNT = Regex("\\{\\d+(?:,\\d*)?\\}")
    }
}
