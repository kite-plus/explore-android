package plus.kite.explore

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.Blog
import plus.kite.explore.data.ExploreJson
import plus.kite.explore.data.SearchPage
import plus.kite.explore.search.SearchModel
import plus.kite.explore.search.SearchTerms

class SearchTermsTest {
    @Test
    fun splitsWords() {
        val cases = listOf(
            "独立开发" to listOf("独立开发"),
            "你好，世界" to listOf("你好", "世界"),
            "Go 语言" to listOf("go", "语言"),
            "go语言" to listOf("go语言"),
            "rust - wasm" to listOf("rust", "wasm"),
            "C++" to listOf("c++"),
            "  " to emptyList(),
        )
        for ((query, words) in cases) assertEquals(query, words, SearchTerms.words(query))
    }

    @Test
    fun keepsEightWords() {
        assertEquals(8, SearchTerms.words("a b c d e f g h i j").size)
    }

    private fun marked(text: String, query: String): List<String> =
        SearchTerms.ranges(SearchTerms.words(query), text).map { text.substring(it) }

    @Test
    fun marksChineseAsTyped() {
        assertEquals(listOf("独立开发"), marked("先回答这四个问题，再谈独立开发", "独立开发"))
        assertEquals(listOf("开发"), marked("独立开发者 1000 用户指南", "开发"))
    }

    @Test
    fun marksOtherWordsWhole() {
        assertEquals(listOf("go", "Go"), marked("Google: go, Go!", "go"))
        assertEquals(listOf("C++"), marked("Learn C++ today", "c++"))
        assertEquals(listOf("go"), marked("go语言入门", "go"))
    }

    @Test
    fun longerWordsWin() {
        assertEquals(listOf("独立开发"), marked("独立开发者", "独立 独立开发"))
    }

    @Test
    fun marksEveryWord() {
        assertEquals(listOf("Swift", "Testing"), marked("SwiftUI and Swift Testing", "testing swift"))
    }

    @Test
    fun marksCharactersOutsideTheBasicPlane() {
        // U+20BB7 is two chars in a Java string; the word after it still counts as whole.
        assertEquals(listOf("𠮷", "go"), marked("𠮷 go", "𠮷 go"))
    }

    @Test
    fun marksWithAStyle() {
        val text = SearchTerms.marked("Learn Rust today", listOf("rust"), Color.Yellow)
        assertEquals("Learn Rust today", text.text)
        val span = text.spanStyles.single()
        assertEquals(6 to 10, span.start to span.end)
        assertEquals(Color.Yellow, span.item.background)
        assertTrue(SearchTerms.marked("Learn Rust", emptyList(), Color.Yellow).spanStyles.isEmpty())
    }

    @Test
    fun snippetsKeepALateMatchInView() {
        val excerpt = "探讨编程方式的质变：如何使用 VSCode、Claude Code 和 OpenClaw 组合打造高效的独立开发工作流。"
        val snippet = SearchTerms.snippet(excerpt, listOf("独立开发"))
        assertTrue(snippet, snippet.startsWith("…"))
        assertTrue(snippet, snippet.endsWith("独立开发工作流。"))
        assertTrue(snippet.length < excerpt.length)
    }

    @Test
    fun snippetsStartAtAWord() {
        val excerpt = "A long introduction that goes on for quite a while before the word appears: rust."
        assertEquals("…appears: rust.", SearchTerms.snippet(excerpt, listOf("rust")))
    }

    @Test
    fun earlyMatchesLeaveTheTextAlone() {
        val excerpt = "观点一：身份错位 —— 独立开发被当成了"
        assertEquals(excerpt, SearchTerms.snippet(excerpt, listOf("独立开发")))
        assertEquals(excerpt, SearchTerms.snippet(excerpt, emptyList()))
    }
}

/** Search against a local server with canned answers. */
class SearchModelTest {
    private val entries = """
    {"data":[{"id":"32817","title":"先回答这四个问题，再谈独立开发","url":"https://www.evan.xin/5124/",
    "blog":{"host":"www.evan.xin","name":"Evan's Space","site_url":"https://www.evan.xin/","language":"zh-Hans"}},
    {"id":"9142","title":"独立开发者 1000 用户指南 Fork","url":"https://ssshooter.com/1000-user-guide/"}],
    "next_cursor":"MToxNzgw","total":1000,"total_capped":true}
    """
    private val blogs = """
    {"data":[{"host":"www.ccgxk.com","name":"串串狗小刊","description":"独立开发","site_url":"https://www.ccgxk.com/"}],
    "next_cursor":null,"total":1,"total_capped":false}
    """

    @Test
    fun decodesLaterPagesWithoutTotals() {
        val page = ExploreJson.decodeFromString(SearchPage.serializer(Blog.serializer()), """{"data":[],"next_cursor":null,"total":null}""")
        assertNull(page.total)
        assertFalse(page.totalCapped)
    }

    @Test
    fun sendsTheQuery() = runBlocking {
        FakeServer { FakeServer.Response(200, """{"data":[],"next_cursor":null,"total":0}""") }.use { server ->
            ApiClient(server.url).searchEntries("C++ 入门", "abc", "zh", 30)
            val path = server.requests.single().path
            assertTrue(path, path.startsWith("/api/v1/search/entries?"))
            // A bare "+" would reach the server as a space.
            assertTrue(path, "q=C%2B%2B%20%E5%85%A5%E9%97%A8" in path)
            assertTrue(path, "cursor=abc" in path && "lang=zh" in path && "limit=30" in path)
        }
    }

    @Test
    fun findsPostsAndBlogs() = runBlocking {
        FakeServer { request ->
            when {
                request.path.startsWith("/api/v1/search/entries") -> FakeServer.Response(200, entries)
                request.path.startsWith("/api/v1/search/blogs") -> FakeServer.Response(200, blogs)
                else -> FakeServer.Response(404)
            }
        }.use { server ->
            val model = SearchModel()
            val query = SearchModel.Query("独立开发", null)
            model.search(query, ApiClient(server.url))
            assertEquals(SearchModel.Phase.Loaded, model.phase)
            assertEquals(query, model.query)
            assertEquals(listOf("32817", "9142"), model.entries.map { it.id })
            assertEquals(1000, model.entryCount)
            assertTrue(model.entryCountCapped)
            assertEquals("MToxNzgw", model.entryCursor)
            assertEquals(listOf("www.ccgxk.com"), model.blogs.map { it.host })
            assertEquals(1, model.blogCount)
            assertNull(model.blogCursor)
            assertTrue(server.requests.any { "limit=10" in it.path && it.path.startsWith("/api/v1/search/blogs") })

            model.reset()
            assertNull(model.query)
            assertTrue(model.isEmpty)
            assertEquals(SearchModel.Phase.Idle, model.phase)
        }
    }

    @Test
    fun blogsAloneSkipPosts() = runBlocking {
        FakeServer { FakeServer.Response(200, blogs) }.use { server ->
            val model = SearchModel(includesPosts = false)
            model.search(SearchModel.Query("独立开发", "zh"), ApiClient(server.url))
            assertEquals(SearchModel.Phase.Loaded, model.phase)
            assertEquals(listOf("www.ccgxk.com"), model.blogs.map { it.host })
            assertTrue(server.requests.all { it.path.startsWith("/api/v1/search/blogs") && "limit=30" in it.path && "lang=zh" in it.path })
        }
    }

    @Test
    fun serversBeforeSearchAreAskedOnce() = runBlocking {
        FakeServer { FakeServer.Response(404, """{"error":{"code":"not_found","message":"Not found."}}""") }.use { server ->
            val model = SearchModel()
            val client = ApiClient(server.url)
            model.search(SearchModel.Query("go", null), client)
            assertEquals(SearchModel.Phase.Unsupported, model.phase)
            val asked = server.requests.size
            model.search(SearchModel.Query("rust", null), client)
            assertEquals(SearchModel.Phase.Unsupported, model.phase)
            assertEquals("rust", model.query?.text)
            assertEquals(asked, server.requests.size)
            // An empty field keeps the fallback, so the next search does not ask again either.
            model.reset()
            assertEquals(SearchModel.Phase.Unsupported, model.phase)
        }
    }

    @Test
    fun proxiesAnswering404FallBackToo() = runBlocking {
        FakeServer { FakeServer.Response(404, "<html>Not Found</html>", listOf("Content-Type" to "text/html")) }.use { server ->
            val model = SearchModel()
            model.search(SearchModel.Query("go", null), ApiClient(server.url))
            assertEquals(SearchModel.Phase.Unsupported, model.phase)
        }
    }

    @Test
    fun failuresKeepTheirMessage() = runBlocking {
        FakeServer { FakeServer.Response(400, """{"error":{"code":"invalid_request","message":"Too long."}}""") }.use { server ->
            val model = SearchModel()
            model.search(SearchModel.Query("x", null), ApiClient(server.url))
            val phase = model.phase as SearchModel.Phase.Failed
            assertEquals("Too long.", (phase.error as ApiError.Server).serverMessage)
        }
    }

    @Test
    fun cancelledSearchesKeepWhatWasShown() = runBlocking {
        FakeServer { request ->
            if ("q=slow" in request.path) Thread.sleep(2000)
            FakeServer.Response(200, if (request.path.startsWith("/api/v1/search/entries")) entries else blogs)
        }.use { server ->
            val model = SearchModel()
            val client = ApiClient(server.url)
            val first = launch { model.search(SearchModel.Query("slow", null), client) }
            delay(300)
            first.cancelAndJoin()
            assertEquals(SearchModel.Phase.Idle, model.phase)

            val query = SearchModel.Query("独立开发", null)
            model.search(query, client)
            val second = launch { model.search(SearchModel.Query("slow", null), client) }
            delay(300)
            assertEquals(SearchModel.Phase.Loading, model.phase)
            second.cancelAndJoin()
            assertEquals(SearchModel.Phase.Loaded, model.phase)
            assertEquals(query, model.query)
        }
    }

    @Test
    fun pagesKeepTheirQuery() = runBlocking {
        FakeServer { request ->
            val body = if ("cursor=" in request.path) {
                """{"data":[{"id":"9142","title":"again","url":"https://a/"},{"id":"77","title":"next","url":"https://b/"}],"next_cursor":null,"total":null}"""
            } else if (request.path.startsWith("/api/v1/search/entries")) {
                entries
            } else {
                blogs
            }
            FakeServer.Response(200, body)
        }.use { server ->
            val model = SearchModel()
            val client = ApiClient(server.url)
            model.search(SearchModel.Query("独立开发", null), client)
            model.loadMoreEntries(client)
            assertEquals(listOf("32817", "9142", "77"), model.entries.map { it.id })
            assertNull(model.entryCursor)
            assertEquals(1000, model.entryCount)
            assertTrue(server.requests.last().path.contains("cursor=MToxNzgw"))
        }
    }
}
