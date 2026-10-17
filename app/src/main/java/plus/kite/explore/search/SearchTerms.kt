package plus.kite.explore.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * The words a search looks for and where they show in a result, matched the way the server
 * matches them (internal/store/search.go in the explore repo, web/src/lib/highlight.ts on the
 * website): Chinese as typed, other words whole.
 */
object SearchTerms {
    const val MAX_WORDS = 8

    /** Scripts written without spaces between words; the server's CJK ranges. */
    private val cjkRanges = listOf(
        0x3040..0x30FF, 0x3400..0x4DBF, 0x4E00..0x9FFF,
        0xAC00..0xD7AF, 0xF900..0xFAFF, 0x20000..0x3134F,
    )

    fun isCJK(codePoint: Int): Boolean = cjkRanges.any { codePoint in it }

    /** A letter or number, as the website's \p{L} and \p{N}. */
    private fun isWordChar(codePoint: Int): Boolean = Character.isLetter(codePoint) || when (Character.getType(codePoint).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    /** A letter or digit outside CJK, which may not touch a whole word. */
    private fun joinsWord(codePoint: Int): Boolean = isWordChar(codePoint) && !isCJK(codePoint)

    /** Splits a query into its words: at spaces, and at punctuation standing alone between them, as in "你好，世界". */
    fun words(query: String): List<String> {
        val words = mutableListOf<String>()
        for (field in query.lowercase().split(Regex("\\s+"))) {
            val word = StringBuilder()
            val run = StringBuilder()
            fun flush() {
                if (run.isEmpty()) return
                val text = run.toString()
                if (!isCJK(text.codePointAt(0)) && text.codePoints().noneMatch { isWordChar(it) }) {
                    if (word.isNotEmpty()) words += word.toString()
                    word.clear()
                } else {
                    word.append(text)
                }
                run.clear()
            }
            var at = 0
            while (at < field.length) {
                val codePoint = field.codePointAt(at)
                if (run.isNotEmpty() && isCJK(codePoint) != isCJK(run.codePointAt(0))) flush()
                run.appendCodePoint(codePoint)
                at += Character.charCount(codePoint)
            }
            flush()
            if (word.isNotEmpty()) words += word.toString()
        }
        return words.take(MAX_WORDS)
    }

    /** Where the words show in a text, in order and never overlapping; longer words win over the shorter ones inside them. */
    fun ranges(words: List<String>, text: String): List<IntRange> {
        val unique = words.filter { it.isNotEmpty() }.distinct().sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        val found = mutableListOf<IntRange>()
        for (word in unique) {
            val wholeStart = !isCJK(word.codePointAt(0))
            val wholeEnd = !isCJK(word.codePointBefore(word.length))
            var from = 0
            while (from < text.length) {
                val start = text.indexOf(word, from, ignoreCase = true)
                if (start < 0) break
                val end = start + word.length
                val match = start until end
                val touches = (wholeStart && start > 0 && joinsWord(text.codePointBefore(start))) ||
                    (wholeEnd && end < text.length && joinsWord(text.codePointAt(end)))
                if (touches || found.any { it.first <= match.last && match.first <= it.last }) {
                    from = start + Character.charCount(text.codePointAt(start))
                } else {
                    found += match
                    from = end
                }
            }
        }
        return found.sortedBy { it.first }
    }

    /**
     * The text from shortly before its first word when that word would sit past what two lines
     * show, so a match in an excerpt stays in view.
     */
    fun snippet(text: String, words: List<String>, lead: Int = 30): String {
        val first = ranges(words, text).firstOrNull() ?: return text
        if (text.codePointCount(0, first.first) <= lead) return text
        var start = text.offsetByCodePoints(first.first, -12)
        // Start at a word in scripts with spaces, not halfway through one.
        val space = (start until first.first).firstOrNull { text[it].isWhitespace() }
        if (space != null) start = space + 1
        return "…" + text.substring(start).trim()
    }

    /** The text with the words marked, as the website marks them. */
    fun marked(text: String, words: List<String>, color: Color): AnnotatedString {
        if (words.isEmpty()) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            for (range in ranges(words, text)) addStyle(SpanStyle(background = color), range.first, range.last + 1)
        }
    }
}
