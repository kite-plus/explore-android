package plus.kite.explore

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import plus.kite.explore.account.siteAddress
import plus.kite.explore.app.BlogIndex
import plus.kite.explore.app.DeepLink
import plus.kite.explore.app.DiscoverStream
import plus.kite.explore.core.Opml
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.Blog
import plus.kite.explore.data.BlogPage
import plus.kite.explore.data.DataList
import plus.kite.explore.data.Entry
import plus.kite.explore.data.ExploreJson
import plus.kite.explore.data.LinkStatus
import plus.kite.explore.data.Notice
import plus.kite.explore.data.OpmlImport
import plus.kite.explore.data.Page
import plus.kite.explore.data.Severity
import plus.kite.explore.data.Submission
import plus.kite.explore.data.SubmissionStatus
import plus.kite.explore.data.Topic
import plus.kite.explore.data.User
import plus.kite.explore.design.Palette
import plus.kite.explore.feed.StreamItem
import plus.kite.explore.feed.linked
import plus.kite.explore.feed.paragraphs

class DecodingTest {
    private inline fun <reified T> decode(serializer: kotlinx.serialization.KSerializer<T>, json: String): T =
        ExploreJson.decodeFromString(serializer, json)

    @Test
    fun entriesPage() {
        val json = """
        {"data":[{"id":"12","title":"Hello","url":"https://blog.example.com/posts/hello/",
        "excerpt":"First paragraph","image_url":"/api/v1/entries/12/image",
        "published_at":"2026-08-29T18:02:33Z","tags":["backend","ops"],"link_status":"available",
        "link_checked_at":"2026-10-02T14:17:01.729921Z",
        "blog":{"host":"blog.example.com","name":"Example Blog","site_url":"https://blog.example.com/","language":"zh-Hans"}}],
        "next_cursor":"MTc4NTcyNDkxMjAwMDAwMDAwMDoxMw"}
        """
        val page = decode(Page.serializer(Entry.serializer()), json)
        val entry = page.data.first()
        assertEquals("12", entry.id)
        assertEquals("/api/v1/entries/12/image", entry.imagePath)
        assertEquals(listOf("backend", "ops"), entry.tags)
        assertEquals(LinkStatus.Available, entry.linkStatus)
        assertNotNull(entry.linkCheckedAt)
        assertEquals("zh-Hans", entry.blog?.language)
        assertEquals("MTc4NTcyNDkxMjAwMDAwMDAwMDoxMw", page.nextCursor)
    }

    @Test
    fun notices() {
        val json = """
        {"data": [
          {"id": "7", "kind": "notice", "title": "Kite for iOS", "summary": "", "url": "", "source_name": "Kite Plus", "position": 0, "published_at": "2026-10-06T00:00:00Z"},
          {"id": "8", "kind": "sponsored", "title": "Hosting", "summary": "Cheap.", "url": "https://ads.example.com/", "source_name": "Acme", "position": 4, "published_at": "2026-10-06T00:00:00Z"}
        ]}
        """
        val list = decode(DataList.serializer(Notice.serializer()), json).data
        assertEquals(listOf("7", "8"), list.map { it.id })
        assertTrue(list[0].isNotice && list[0].isPinned)
        // A kind the app does not know shows as an ad, never unmarked.
        assertFalse(list[1].isNotice || list[1].isPinned)
        assertEquals("Acme", list[1].sourceName)
        assertNull(list[0].body)
        val one = decode(Notice.serializer(), """{"id": "7", "kind": "notice", "title": "x", "source_name": "Kite Plus", "position": 0, "body": "a\n\nb"}""")
        assertEquals("a\n\nb", one.body)
    }

    @Test
    fun entryWithoutExcerptOrBlog() {
        val entry = decode(Entry.serializer(), """{"id":"3","title":"Bare","url":"https://a.example/p","excerpt":null,"published_at":null,"link_status":"something-new"}""")
        assertNull(entry.excerpt)
        assertNull(entry.blog)
        assertNull(entry.publishedAt)
        assertEquals(emptyList<String>(), entry.tags)
        assertEquals(LinkStatus.Unknown, entry.linkStatus)
    }

    @Test
    fun blogPage() {
        val json = """
        {"blog":{"host":"blog.example.com","name":"Example","description":"About it","site_url":"https://blog.example.com/",
        "feed_url":"https://blog.example.com/feed.xml","language":"en","generator":"hugo","last_published_at":"2026-10-01T08:00:00Z"},
        "entries":[],"next_cursor":null}
        """
        val page = decode(BlogPage.serializer(), json)
        assertEquals("About it", page.blog.about)
        assertEquals("hugo", page.blog.generator)
        assertNull(page.nextCursor)
    }

    @Test
    fun blogWithoutOptionalFields() {
        val blog = decode(Blog.serializer(), """{"host":"a.example","name":"A","site_url":"https://a.example/"}""")
        assertEquals("", blog.about)
        assertEquals("unknown", blog.generator)
    }

    @Test
    fun topics() {
        val topics = decode(DataList.serializer(Topic.serializer()), """{"data":[{"slug":"ai","name":{"zh":"人工智能","en":"AI"}}]}""").data
        assertEquals("人工智能", topics[0].name(plus.kite.explore.data.AppLanguage.Zh))
        assertEquals("AI", topics[0].name(plus.kite.explore.data.AppLanguage.En))
    }

    @Test
    fun user() {
        val user = decode(User.serializer(), """{"id":"9f1","email":"a@example.com","display_name":"A","is_admin":false,"csrf_token":"abc"}""")
        assertEquals("A", user.displayName)
        assertEquals("abc", user.csrfToken)
        assertFalse(user.temporaryPassword)
    }

    @Test
    fun userWithTemporaryPassword() {
        val user = decode(User.serializer(), """{"id":"9f1","email":"a@example.com","display_name":"A","is_admin":false,"csrf_token":"abc","temporary_password":true}""")
        assertTrue(user.temporaryPassword)
    }

    @Test
    fun opmlImport() {
        val json = """
        {"outlines":4,"added":2,"already_following":1,"ignored":0,
        "not_listed":[{"title":"Elsewhere","site_url":"","feed_url":"https://elsewhere.example/feed.xml"}]}
        """
        val result = decode(OpmlImport.serializer(), json)
        assertEquals(4, result.outlines)
        assertEquals(1, result.alreadyFollowing)
        assertEquals("https://elsewhere.example/feed.xml", result.notListed.first().feedUrl)
        assertEquals("https://elsewhere.example/", siteAddress(result.notListed.first()))
    }

    @Test
    fun submission() {
        val json = """
        {"id":"8c1","status":"pending","host":"blog.example.com","site_url":"https://blog.example.com/","feed_url":"https://blog.example.com/feed.xml",
        "check_report":{"input_url":"https://blog.example.com/","passed":true,"problems":[{"code":"no_conditional_get","severity":"info"}]},
        "review_note":null,"created_at":"2026-10-01T08:00:00Z","reviewed_at":null}
        """
        val submission = decode(Submission.serializer(), json)
        assertEquals(SubmissionStatus.Pending, submission.status)
        assertEquals(Severity.Info, submission.checkReport?.problems?.first()?.severity)
        assertNull(submission.reviewNote)
    }

    @Test
    fun checkFailedError() {
        val json = """{"error":{"code":"check_failed","message":"The blog did not pass the check."},"check_report":{"input_url":"https://x.example/","passed":false,"problems":[{"code":"feed_not_found","severity":"error"}]}}"""
        val error = ApiError.from(422, json) as ApiError.Server
        assertEquals("check_failed", error.code)
        assertEquals("feed_not_found", error.report?.problems?.first()?.code)
        assertEquals("The blog did not pass the check.", error.serverMessage)
    }

    @Test
    fun pendingErrorCarriesSubmission() {
        val error = ApiError.from(409, """{"error":{"code":"already_pending","message":"Waiting."},"submission_id":"abc"}""") as ApiError.Server
        assertEquals("abc", error.submissionId)
    }

    @Test
    fun proxyErrorsStillMapToCodes() {
        assertEquals("internal", ApiError.from(502, "<html>Bad gateway</html>").code)
        assertEquals("rate_limited", ApiError.from(429, "").code)
        assertTrue(ApiError.from(401, "").isUnauthorized)
    }

    @Test
    fun readsTheSessionCookie() {
        val headers = mapOf<String?, List<String>>(
            null to listOf("HTTP/1.1 200 OK"),
            "set-cookie" to listOf("other=1; Path=/", "explore_session=tok123; Path=/; HttpOnly; SameSite=Lax"),
        )
        assertEquals("tok123", ApiClient.sessionCookie(headers))
        assertNull(ApiClient.sessionCookie(mapOf("Set-Cookie" to listOf("other=1"))))
    }
}

class PaletteTest {
    // Expected values come from the website's avatarColor in lib/avatar.ts, as the iOS tests have them.
    @Test
    fun matchesTheWebsite() {
        val cases = mapOf("blog.hikarilan.life" to 5, "www.amigoer.com" to 4, "example.com" to 7, "blog.example.com" to 3, "中文.example" to 4)
        for ((host, index) in cases) assertEquals(host, index, Palette.avatarIndex(host))
    }

    @Test
    fun initials() {
        assertEquals("K", Palette.initial("  kite "))
        assertEquals("贺", Palette.initial("贺兰星辰"))
        assertEquals("?", Palette.initial(""))
        assertEquals(Color(0xFF0284C7), Palette.color("blog.hikarilan.life"))
    }
}

class StreamItemTest {
    private fun entry(id: String) = Entry(id = id, title = id, url = "https://blog.example.com/$id")

    private fun notice(id: String, position: Int) = Notice(id = id, kind = "notice", title = id, sourceName = "Kite Plus", position = position)

    @Test
    fun placesNoticesByPosition() {
        val items = StreamItem.items(listOf(entry("a"), entry("b")), listOf(notice("pinned", 0), notice("first", 1), notice("late", 9)))
        assertEquals(listOf("notice-pinned", "post-a", "notice-first", "post-b", "notice-late"), items.map { it.key })
    }

    @Test
    fun noNoticesLeavesThePosts() {
        assertEquals(listOf("post-a"), StreamItem.items(listOf(entry("a")), emptyList()).map { it.key })
    }

    @Test
    fun splitsNoticeTextIntoParagraphs() {
        val text = "第一段。\r\n\r\n第二段\n同一段。\n  \n\n第三段。\n"
        assertEquals(listOf("第一段。", "第二段\n同一段。", "第三段。"), paragraphs(text))
    }

    @Test
    fun linksAddressesInNoticeText() {
        val text = linked("了解更多：www.kite.plus 或 https://explore.kite.plus/about。", Color.Blue) {}
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://www.kite.plus", "https://explore.kite.plus/about"), links)
    }
}

class DeepLinkTest {
    @Test
    fun opensWebsiteAddresses() {
        assertEquals(DeepLink.Blog("blog.example.com"), DeepLink.parse("https://explore.kite.plus/blogs/Blog.Example.com"))
        assertEquals(DeepLink.Blog("blog.example.com"), DeepLink.parse("https://explore.kite.plus/en/blogs/blog.example.com"))
        assertEquals(DeepLink.Topic("ai"), DeepLink.parse("explore://topics/ai"))
        assertEquals(DeepLink.Submission("abc"), DeepLink.parse("https://explore.kite.plus/submissions/abc"))
    }

    @Test
    fun opensSheetsAndTabs() {
        assertEquals(DeepLink.Submit("", ""), DeepLink.parse("explore://submit"))
        assertEquals(
            DeepLink.Submit("https://a.example/", "https://a.example/feed.xml"),
            DeepLink.parse("https://explore.kite.plus/submit?site=https%3A%2F%2Fa.example%2F&feed=https%3A%2F%2Fa.example%2Ffeed.xml"),
        )
        assertEquals(DeepLink.Stream(DiscoverStream.Following), DeepLink.parse("explore://following"))
        assertEquals(DeepLink.Stream(DiscoverStream.Recommended), DeepLink.parse("https://explore.kite.plus/en/recommended"))
        assertEquals(DeepLink.Stream(DiscoverStream.Latest), DeepLink.parse("https://explore.kite.plus/"))
        assertEquals(DeepLink.DiscoverTab, DeepLink.parse("explore://nowhere"))
        assertEquals(DeepLink.SignIn, DeepLink.parse("explore://sign-in"))
        assertEquals(DeepLink.MeTab, DeepLink.parse("explore://me"))
    }

    @Test
    fun opensNoticesAndSearchOnDiscover() {
        assertEquals(DeepLink.NoticePage("7"), DeepLink.parse("explore://notices/7"))
        assertEquals(DeepLink.Search, DeepLink.parse("explore://search"))
    }

    @Test
    fun opensPostsOnlyOnTheWeb() {
        assertEquals(DeepLink.OpenPost("https://a.example/p?x=1"), DeepLink.parse("explore://open?url=https%3A%2F%2Fa.example%2Fp%3Fx%3D1"))
        assertEquals(DeepLink.DiscoverTab, DeepLink.parse("explore://open?url=javascript%3Aalert(1)"))
    }
}

class BlogIndexTest {
    private val blogs = listOf(
        Blog(host = "blog.example.com", name = "Example Notes", about = "On Go and databases", siteUrl = "https://blog.example.com/"),
        Blog(host = "kite.plus", name = "Kite", about = "博客引擎", siteUrl = "https://kite.plus/"),
    )

    @Test
    fun matchesEveryWordInNameHostOrDescription() {
        assertEquals(listOf("blog.example.com"), BlogIndex.match(blogs, "go notes").map { it.host })
        assertEquals(listOf("kite.plus"), BlogIndex.match(blogs, "博客").map { it.host })
        assertEquals(emptyList<Blog>(), BlogIndex.match(blogs, "   "))
    }
}

class OpmlDocumentTest {
    @Test
    fun listsFollowedBlogs() {
        val blog = Blog(host = "a.example", name = "A & B", siteUrl = "https://a.example/", feedUrl = "https://a.example/feed.xml")
        val document = Opml.document("Blogs", listOf(blog), java.time.Instant.ofEpochSecond(0))
        assertTrue(document.contains("""<outline type="rss" text="A &amp; B" title="A &amp; B" xmlUrl="https://a.example/feed.xml" htmlUrl="https://a.example/"/>"""))
        assertTrue(document.contains("<dateCreated>1970-01-01T00:00:00Z</dateCreated>"))
    }
}
