package plus.kite.explore.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.Blog
import plus.kite.explore.data.Entry
import plus.kite.explore.data.Notice
import plus.kite.explore.data.isCancellation

/**
 * One stream of posts, paged with the server's cursor: the latest posts, the recommended
 * ones, the reader's follows, one tag, or one blog.
 */
class FeedModel(val source: Source, tag: String? = null) {
    sealed interface Source {
        data object Latest : Source
        data object Recommended : Source
        data object Following : Source
        data class Blog(val host: String) : Source
    }

    sealed interface Phase {
        data object Idle : Phase
        data object Loading : Phase
        data object Loaded : Phase
        data class Failed(val error: Throwable) : Phase
    }

    var language: LanguageFilter = LanguageFilter.All
    var tag: String? = tag

    /** Changes when what the list shows changes on the server's side, such as the account or its follows. */
    var version = 0

    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    /** What Explore itself shows between the posts: only on the latest stream without a tag, as on the website. */
    var notices by mutableStateOf<List<Notice>>(emptyList())
        private set
    var blog by mutableStateOf<Blog?>(null)
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var loadMoreError by mutableStateOf<Throwable?>(null)
        private set

    /** The error behind a failed first page, for callers that react to it. */
    var lastError: ApiError? = null
        private set
    private var loadedKey: String? = null
    private var loadedAt: Instant? = null

    val hasMore: Boolean get() = nextCursor != null

    val showsPlaceholders: Boolean get() = (phase == Phase.Idle || phase == Phase.Loading) && entries.isEmpty()

    private val showsNotices: Boolean get() = source == Source.Latest && tag == null

    private fun key(client: ApiClient) = "${client.server}|$source|${language.key}|${tag.orEmpty()}|$version"

    /** Notices are extra: when they cannot be had, the ones shown stay. */
    private suspend fun fetchNotices(client: ApiClient, fresh: Boolean): List<Notice> {
        if (!showsNotices) return emptyList()
        return try {
            client.notices(fresh)
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            notices
        }
    }

    /**
     * Shows the list when a screen appears. A list already loaded for the same filters stays
     * as it is, pages and all; after ten minutes it gains the newer posts on top.
     */
    suspend fun refreshIfNeeded(client: ApiClient) {
        val at = loadedAt
        if (loadedKey != key(client) || phase != Phase.Loaded || entries.isEmpty() || at == null) {
            load(client)
            return
        }
        if (Instant.now().epochSecond - at.epochSecond <= 600) return
        try {
            coroutineScope {
                val pendingNotices = async { fetchNotices(client, fresh = true) }
                val page = fetch(client, null, fresh = true)
                ensureActive()
                val known = entries.map { it.id }.toSet()
                val newer = page.entries.takeWhile { it.id !in known }
                if (newer.size == page.entries.size) {
                    // Nothing on the first page is known: start over rather than leave a gap.
                    entries = unique(page.entries)
                    nextCursor = page.next
                } else {
                    entries = newer + entries
                }
                page.blog?.let { blog = it }
                notices = pendingNotices.await()
                loadedAt = Instant.now()
            }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            // The list on screen is still right for its filters.
        }
    }

    /** Loads the first page. Content for the same filters stays while it refreshes; new filters start over. */
    suspend fun load(client: ApiClient, fresh: Boolean = false) {
        val requestKey = key(client)
        if (loadedKey != requestKey) {
            entries = emptyList()
            notices = emptyList()
            blog = null
            nextCursor = null
            phase = Phase.Loading
        } else if (entries.isEmpty()) {
            phase = Phase.Loading
        }
        try {
            coroutineScope {
                val pendingNotices = async { fetchNotices(client, fresh) }
                val page = fetch(client, null, fresh)
                ensureActive()
                entries = unique(page.entries)
                notices = pendingNotices.await()
                nextCursor = page.next
                page.blog?.let { blog = it }
                loadedKey = requestKey
                loadedAt = Instant.now()
                loadMoreError = null
                lastError = null
                phase = Phase.Loaded
            }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            lastError = error as? ApiError
            phase = if (loadedKey == requestKey && entries.isNotEmpty()) Phase.Loaded else Phase.Failed(error)
        }
    }

    suspend fun loadMore(client: ApiClient) {
        val cursor = nextCursor ?: return
        if (isLoadingMore || phase != Phase.Loaded) return
        isLoadingMore = true
        loadMoreError = null
        try {
            val page = fetch(client, cursor, fresh = false)
            val known = entries.map { it.id }.toSet()
            entries = entries + page.entries.filter { it.id !in known }
            nextCursor = page.next
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            loadMoreError = error
        } finally {
            isLoadingMore = false
        }
    }

    private class PageResult(val entries: List<Entry>, val next: String?, val blog: Blog?)

    private suspend fun fetch(client: ApiClient, cursor: String?, fresh: Boolean): PageResult = when (val source = source) {
        Source.Latest -> client.entries(cursor, language.code, tag, fresh = fresh).let { PageResult(it.data, it.nextCursor, null) }
        Source.Recommended -> client.entries(cursor, language.code, tag, order = "recommended", fresh = fresh)
            .let { PageResult(it.data, it.nextCursor, null) }
        Source.Following -> client.followingEntries(cursor, language.code, tag).let { PageResult(it.data, it.nextCursor, null) }
        is Source.Blog -> client.blog(source.host, cursor, fresh).let { PageResult(it.entries, it.nextCursor, it.blog) }
    }

    private fun unique(list: List<Entry>): List<Entry> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it.id) }
    }
}

/** A post or a notice, in the order a stream shows them. */
sealed interface StreamItem {
    val key: String

    data class Post(val entry: Entry) : StreamItem {
        override val key: String get() = "post-${entry.id}"
    }

    data class Note(val notice: Notice) : StreamItem {
        override val key: String get() = "notice-${notice.id}"
    }

    companion object {
        /**
         * Notices go between the posts by position: 0 before the first post, N after the Nth,
         * and one past the end goes last. Those sharing a place keep the server's order.
         */
        fun items(entries: List<Entry>, notices: List<Notice>): List<StreamItem> {
            val items = ArrayList<StreamItem>(entries.size + notices.size)
            fun placed(index: Int) = notices.filter { minOf(it.position, entries.size) == index }.map { Note(it) }
            entries.forEachIndexed { index, entry ->
                items += placed(index)
                items += Post(entry)
            }
            items += placed(entries.size)
            return items
        }
    }
}
