package plus.kite.explore.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import plus.kite.explore.data.Blog

/** An OPML 2.0 list of followed blogs, the format every feed reader imports. */
object Opml {
    fun document(title: String, blogs: List<Blog>, created: Instant = Instant.now()): String {
        val lines = mutableListOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<opml version="2.0">""",
            "  <head>",
            "    <title>${escape(title)}</title>",
            "    <dateCreated>${DateTimeFormatter.ISO_INSTANT.format(created)}</dateCreated>",
            "  </head>",
            "  <body>",
        )
        for (blog in blogs) {
            val name = escape(blog.name)
            lines += """    <outline type="rss" text="$name" title="$name" xmlUrl="${escape(blog.feedUrl)}" htmlUrl="${escape(blog.siteUrl)}"/>"""
        }
        lines += "  </body>"
        lines += "</opml>"
        return lines.joinToString("\n") + "\n"
    }

    fun escape(text: String): String = buildString(text.length) {
        for (char in text) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    /** Explore reads OPML files up to 2 MiB. */
    const val MAX_BYTES = 2 shl 20
}
