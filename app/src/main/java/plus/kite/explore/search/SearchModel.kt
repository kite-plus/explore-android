package plus.kite.explore.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.Blog
import plus.kite.explore.data.Entry
import plus.kite.explore.data.isCancellation

/** One search on the server: the posts and the blogs that match a query, each paged with its own cursor. */
class SearchModel(
    /** False for blogs alone, as the Blogs tab searches. */
    val includesPosts: Boolean = true,
) {
    sealed interface Phase {
        data object Idle : Phase
        data object Loading : Phase
        data object Loaded : Phase
        data class Failed(val error: Throwable) : Phase

        /** The server predates search; blogs are matched on the device. */
        data object Unsupported : Phase
    }

    data class Query(val text: String, val language: String?)

    val blogPageSize: Int = if (includesPosts) 10 else 30

    /** What the results on screen answer, which lags the field while a new search runs. */
    var query by mutableStateOf<Query?>(null)
        private set
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    var blogs by mutableStateOf<List<Blog>>(emptyList())
        private set

    /** How many posts match, counted up to 1,000; see entryCountCapped. */
    var entryCount by mutableIntStateOf(0)
        private set
    var entryCountCapped by mutableStateOf(false)
        private set
    var blogCount by mutableIntStateOf(0)
        private set
    var entryCursor by mutableStateOf<String?>(null)
        private set
    var blogCursor by mutableStateOf<String?>(null)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    var isLoadingMoreEntries by mutableStateOf(false)
        private set
    var isLoadingMoreBlogs by mutableStateOf(false)
        private set
    var entryPageError by mutableStateOf<Throwable?>(null)
        private set
    var blogPageError by mutableStateOf<Throwable?>(null)
        private set
    private var server: String? = null

    /** A server that answered 404, so it is not asked again. */
    private var serverWithoutSearch: String? = null

    val isEmpty: Boolean get() = entries.isEmpty() && blogs.isEmpty()

    /** Runs a search. The results on screen stay until the new ones come, so typing does not flash placeholders. */
    suspend fun search(query: Query, client: ApiClient) {
        if (serverWithoutSearch == client.server) {
            show(query, client)
            phase = Phase.Unsupported
            return
        }
        if (this.query == query && server == client.server && phase == Phase.Loaded) return
        if (server != client.server) clear()
        phase = Phase.Loading
        try {
            coroutineScope {
                val foundBlogs = async { client.searchBlogs(query.text, null, query.language, blogPageSize) }
                val entryPage = if (includesPosts) client.searchEntries(query.text, null, query.language, ENTRY_PAGE_SIZE) else null
                val blogPage = foundBlogs.await()
                ensureActive()
                show(query, client)
                entries = entryPage?.data.orEmpty().distinctBy { it.id }
                blogs = blogPage.data.distinctBy { it.host }
                entryCount = entryPage?.total ?: entries.size
                entryCountCapped = entryPage?.totalCapped ?: false
                blogCount = blogPage.total ?: blogs.size
                entryCursor = entryPage?.nextCursor
                blogCursor = blogPage.nextCursor
                phase = Phase.Loaded
            }
        } catch (error: Exception) {
            if (error.isCancellation) {
                // A search cut short leaves the results on screen as they were.
                if (phase == Phase.Loading) phase = if (this.query != null) Phase.Loaded else Phase.Idle
                throw error
            }
            if (error is ApiError && error.status == 404) {
                serverWithoutSearch = client.server
                show(query, client)
                phase = Phase.Unsupported
            } else {
                phase = Phase.Failed(error)
            }
        }
    }

    /** Forgets the results once the field is empty, so the next search does not open on the last one's. */
    fun reset() {
        clear()
        if (phase != Phase.Unsupported) phase = Phase.Idle
    }

    suspend fun loadMoreEntries(client: ApiClient) {
        val query = query ?: return
        val cursor = entryCursor ?: return
        if (isLoadingMoreEntries || phase != Phase.Loaded) return
        isLoadingMoreEntries = true
        entryPageError = null
        try {
            val page = client.searchEntries(query.text, cursor, query.language, ENTRY_PAGE_SIZE)
            if (this.query != query) return
            entries = (entries + page.data).distinctBy { it.id }
            entryCursor = page.nextCursor
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            if (this.query == query) entryPageError = error
        } finally {
            isLoadingMoreEntries = false
        }
    }

    suspend fun loadMoreBlogs(client: ApiClient) {
        val query = query ?: return
        val cursor = blogCursor ?: return
        if (isLoadingMoreBlogs || phase != Phase.Loaded) return
        isLoadingMoreBlogs = true
        blogPageError = null
        try {
            val page = client.searchBlogs(query.text, cursor, query.language, blogPageSize)
            if (this.query != query) return
            blogs = (blogs + page.data).distinctBy { it.host }
            blogCursor = page.nextCursor
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            if (this.query == query) blogPageError = error
        } finally {
            isLoadingMoreBlogs = false
        }
    }

    private fun show(query: Query, client: ApiClient) {
        clear()
        this.query = query
        server = client.server
    }

    private fun clear() {
        query = null
        entries = emptyList()
        blogs = emptyList()
        entryCount = 0
        entryCountCapped = false
        blogCount = 0
        entryCursor = null
        blogCursor = null
        entryPageError = null
        blogPageError = null
    }

    companion object {
        const val ENTRY_PAGE_SIZE = 30
    }
}
