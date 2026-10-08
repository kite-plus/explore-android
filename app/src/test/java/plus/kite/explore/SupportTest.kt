package plus.kite.explore

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import plus.kite.explore.core.Formatting
import plus.kite.explore.core.Opml
import plus.kite.explore.core.QrCode
import plus.kite.explore.core.SourceTag
import plus.kite.explore.data.Rfc3339

class QrCodeTest {
    private fun decode(code: QrCode): String {
        val matrix = BitMatrix(code.size)
        for (y in 0 until code.size) for (x in 0 until code.size) if (code.isDark(x, y)) matrix.set(x, y)
        return Decoder().decode(matrix).text
    }

    @Test
    fun codeReadsBackAsTheLink() {
        val links = listOf(
            "https://explore.kite.plus/p/12345",
            "https://explore.kite.plus/en/blogs/www.example.com",
            "https://explore.kite.plus/p/" + "9".repeat(180),
            "https://explore.kite.plus/blogs/例子.中国",
        )
        for (link in links) assertEquals(link, decode(QrCode.encode(link)))
    }

    @Test
    fun shortLinksStaySparse() {
        assertTrue(QrCode.encode("https://explore.kite.plus/p/12345").size <= 33)
    }
}

class SourceTagTest {
    @Test
    fun addsSource() {
        assertEquals("https://a.example/post?utm_source=explore.kite.plus", SourceTag.tagged("https://a.example/post", "explore.kite.plus"))
    }

    @Test
    fun keepsTheQueryAsWritten() {
        assertEquals(
            "https://a.example/p?id=1&x=%20y&utm_source=explore.kite.plus",
            SourceTag.tagged("https://a.example/p?id=1&x=%20y", "explore.kite.plus"),
        )
    }

    @Test
    fun keepsTheFragment() {
        assertEquals("https://a.example/p?utm_source=localhost:8080#top", SourceTag.tagged("https://a.example/p#top", "localhost:8080"))
    }

    @Test
    fun leavesTheAuthorsSourceAlone() {
        assertEquals("https://a.example/p?utm_source=rss", SourceTag.tagged("https://a.example/p?utm_source=rss", "explore.kite.plus"))
    }

    @Test
    fun leavesOtherSchemesAlone() {
        assertEquals("mailto:a@example.com", SourceTag.tagged("mailto:a@example.com", "explore.kite.plus"))
    }
}

class FormattingTest {
    @Test
    fun normalizesAddresses() {
        assertEquals("https://blog.example.com", Formatting.normalizedAddress(" blog.example.com "))
        assertEquals("http://blog.example.com", Formatting.normalizedAddress("http://blog.example.com"))
        assertEquals("", Formatting.normalizedAddress("   "))
    }

    @Test
    fun displayHosts() {
        assertEquals("example.com", Formatting.displayHost("www.example.com"))
        assertEquals("blog.example.com", Formatting.displayHost("blog.example.com"))
    }

    @Test
    fun webAddresses() {
        assertTrue(Formatting.isWeb("https://a.example/p"))
        assertTrue(!Formatting.isWeb("javascript:alert(1)"))
        assertTrue(!Formatting.isWeb("intent://x#Intent;end"))
        assertEquals("a.example", Formatting.hostOf("https://A.example/p"))
    }
}

class Rfc3339Test {
    @Test
    fun parsesWholeSeconds() {
        assertEquals(Instant.ofEpochSecond(1_789_869_600), Rfc3339.parse("2026-09-20T02:00:00Z"))
    }

    @Test
    fun parsesMicroseconds() {
        val date = Rfc3339.parse("2026-10-02T14:17:01.729921Z")!!
        assertEquals(729_921_000, date.nano)
    }

    @Test
    fun parsesOffsets() {
        assertEquals(Rfc3339.parse("2026-09-20T02:00:00Z"), Rfc3339.parse("2026-09-20T10:00:00+08:00"))
    }

    @Test
    fun rejectsGarbage() {
        for (text in listOf("", "yesterday", "2026-13-45T99:00:00Z")) assertNull(Rfc3339.parse(text))
    }
}

class OpmlTest {
    @Test
    fun escapesNames() {
        assertEquals("A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;", Opml.escape("A & B <C> \"D\" 'E'"))
    }
}
