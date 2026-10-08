package plus.kite.explore.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.Blog
import plus.kite.explore.data.isCancellation

/**
 * The whole blog list, kept on the device so search answers as you type. Explore has no
 * search endpoint; its directory is small enough to hold.
 */
class BlogIndex {
    var blogs by mutableStateOf<List<Blog>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    private var loadedServer: String? = null

    suspend fun loadIfNeeded(client: ApiClient) {
        if (loadedServer == client.server || isLoading) return
        isLoading = true
        try {
            val all = mutableListOf<Blog>()
            var cursor: String? = null
            do {
                val page = client.blogs(cursor, null, limit = 100)
                all += page.data
                cursor = page.nextCursor
            } while (cursor != null && all.size < 3000)
            blogs = all
            loadedServer = client.server
            error = null
        } catch (failure: Throwable) {
            if (failure.isCancellation) throw failure
            error = failure
        } finally {
            isLoading = false
        }
    }

    fun matches(query: String): List<Blog> = match(blogs, query)

    companion object {
        /** Blogs whose name, host or description holds every word of the query, ignoring case. */
        fun match(blogs: List<Blog>, query: String): List<Blog> {
            val terms = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (terms.isEmpty()) return emptyList()
            return blogs.filter { blog ->
                terms.all { term ->
                    blog.name.contains(term, ignoreCase = true) ||
                        blog.host.contains(term, ignoreCase = true) ||
                        blog.about.contains(term, ignoreCase = true)
                }
            }
        }
    }
}
