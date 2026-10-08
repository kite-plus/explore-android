package plus.kite.explore.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import plus.kite.explore.BuildConfig
import plus.kite.explore.R

/**
 * Talks to one Explore server (docs/design/api.md in the explore repo).
 *
 * Anonymous requests carry no cookie at all. Once signed in, account requests send the
 * session cookie by hand and writes add the CSRF token; the token is kept encrypted on the
 * device rather than in a shared cookie jar.
 */
class ApiClient(server: String, @Volatile var sessionToken: String? = null) {
    /** The server's address without a trailing slash. */
    val server: String = server.trimEnd('/')

    @Volatile var csrfToken: String? = null

    // Addresses

    fun address(path: String): String = server + "/" + path.removePrefix("/")

    fun imageUrl(path: String): String = address(path)

    fun faviconUrl(host: String): String = address("api/v1/blogs/$host/favicon")

    /** The blog's page on the Explore website, for sharing. */
    fun webBlogUrl(host: String): String = server + AppLanguage.current.webPrefix + "/blogs/$host"

    /** A post's share page on the Explore website, which names the blog and leads on to the author's site. */
    fun webPostUrl(id: String): String = server + AppLanguage.current.webPrefix + "/p/$id"

    /** A notice's own page on the Explore website. */
    fun webNoticeUrl(id: String): String = server + AppLanguage.current.webPrefix + "/notices/$id"

    val feedUrl: String get() = address("feed.xml")
    val opmlUrl: String get() = address("blogs.opml")
    val botUrl: String get() = address("bot")

    /** The server's host, for display. */
    val host: String get() = server.substringAfter("://").substringBefore('/')

    // Reading

    /**
     * The latest posts, or with order "recommended" the posts that score high enough, each
     * day's best first. Their days follow the device's time zone.
     */
    suspend fun entries(
        cursor: String?, language: String?, tag: String?, order: String? = null, limit: Int? = null, fresh: Boolean = false,
    ): Page<Entry> {
        val query = listQuery(cursor, language, tag)
        if (order != null) query += "order" to order
        if (order == "recommended") query += "tz" to ZoneId.systemDefault().id
        if (limit != null) query += "limit" to limit.toString()
        return get("api/v1/entries", Page.serializer(Entry.serializer()), query, fresh = fresh)
    }

    suspend fun followingEntries(cursor: String?, language: String?, tag: String?): Page<Entry> =
        get("api/v1/me/entries", Page.serializer(Entry.serializer()), listQuery(cursor, language, tag), signed = true)

    suspend fun blogs(cursor: String?, language: String?, limit: Int = 30, fresh: Boolean = false): Page<Blog> {
        val query = listQuery(cursor, language, null)
        query += "limit" to limit.toString()
        return get("api/v1/blogs", Page.serializer(Blog.serializer()), query, fresh = fresh)
    }

    suspend fun blog(host: String, cursor: String?, fresh: Boolean = false): BlogPage =
        get("api/v1/blogs/$host", BlogPage.serializer(), listQuery(cursor, null, null), fresh = fresh)

    suspend fun topics(): List<Topic> = get("api/v1/tags", DataList.serializer(Topic.serializer())).data

    /** The notices and ads showing now for the interface language. */
    suspend fun notices(fresh: Boolean = false): List<Notice> =
        get(
            "api/v1/notices", DataList.serializer(Notice.serializer()),
            mutableListOf("audience" to AppLanguage.current.code), fresh = fresh,
        ).data

    /** One notice that is showing, with its text. */
    suspend fun notice(id: String): Notice = get("api/v1/notices/$id", Notice.serializer(), fresh = true)

    suspend fun siteConfig(): SiteConfig = get("api/v1/site-config", SiteConfig.serializer(), fresh = true)

    // Link checks

    suspend fun linkState(entryId: String): LinkState =
        get("api/v1/entries/$entryId/check", LinkState.serializer(), fresh = true)

    suspend fun checkLink(entryId: String): LinkState =
        send("POST", "api/v1/entries/$entryId/check", LinkState.serializer(), JsonObject(emptyMap()))

    // Submissions

    suspend fun previewSubmission(siteUrl: String, feedUrl: String): SubmissionPreview =
        send(
            "POST", "api/v1/submissions/preview", SubmissionPreview.serializer(),
            json("site_url" to siteUrl, "feed_url" to feedUrl), timeoutSeconds = 45,
        )

    suspend fun submit(siteUrl: String, feedUrl: String, note: String, title: String, description: String): Submission =
        send(
            "POST", "api/v1/submissions", Submission.serializer(),
            json(
                "site_url" to siteUrl, "feed_url" to feedUrl, "note" to note,
                "title" to title, "description" to description,
            ),
            timeoutSeconds = 45,
        )

    suspend fun submission(id: String): Submission = get("api/v1/submissions/$id", Submission.serializer(), fresh = true)

    // Account

    /** Signs in and returns the user with the new session token. */
    suspend fun signIn(email: String, password: String): Pair<User, String> =
        startSession("api/v1/auth/login", json("email" to email, "password" to password))

    suspend fun register(email: String, password: String, displayName: String): Pair<User, String> =
        startSession(
            "api/v1/auth/register",
            json("email" to email, "password" to password, "display_name" to displayName),
        )

    suspend fun signOut() {
        sendEmpty("POST", "api/v1/auth/logout", JsonObject(emptyMap()))
    }

    suspend fun me(): User = get("api/v1/me", User.serializer(), signed = true)

    suspend fun updateName(name: String): User =
        send("PATCH", "api/v1/me", User.serializer(), json("display_name" to name), signed = true)

    suspend fun changePassword(current: String, new: String) {
        sendEmpty("PUT", "api/v1/me/password", json("current_password" to current, "new_password" to new))
    }

    suspend fun deleteAccount() {
        sendEmpty("DELETE", "api/v1/me", null)
    }

    suspend fun subscriptions(): List<Blog> =
        get("api/v1/me/subscriptions", DataList.serializer(Blog.serializer()), signed = true).data

    suspend fun subscribe(host: String) {
        sendEmpty("PUT", "api/v1/me/subscriptions/$host", null)
    }

    suspend fun unsubscribe(host: String) {
        sendEmpty("DELETE", "api/v1/me/subscriptions/$host", null)
    }

    /** Follows the blogs an OPML file lists that Explore lists too. */
    suspend fun importSubscriptions(opml: ByteArray): OpmlImport {
        val response = perform(
            "POST", "api/v1/me/subscriptions/import", body = opml, contentType = "text/x-opml",
            signed = true, timeoutSeconds = 60,
        )
        return decode(OpmlImport.serializer(), response.text)
    }

    suspend fun ownedBlogs(): List<Blog> = get("api/v1/me/blogs", DataList.serializer(Blog.serializer()), signed = true).data

    suspend fun startClaim(host: String): ClaimChallenge =
        send("POST", "api/v1/me/blog-claims/$host", ClaimChallenge.serializer(), JsonObject(emptyMap()), signed = true)

    suspend fun verifyClaim(host: String) {
        sendEmpty("POST", "api/v1/me/blog-claims/$host/verify", JsonObject(emptyMap()))
    }

    suspend fun report(blogHost: String, entryId: String?, reason: String) {
        val body = buildJsonObject {
            put("target_type", JsonPrimitive(if (entryId == null) "blog" else "entry"))
            put("blog_host", JsonPrimitive(blogHost))
            put("reason", JsonPrimitive(reason))
            entryId?.toLongOrNull()?.let { put("entry_id", JsonPrimitive(it)) }
        }
        sendEmpty("POST", "api/v1/reports", body)
    }

    // Plumbing

    private class Response(val status: Int, val text: String, val headers: Map<String?, List<String>>)

    private fun listQuery(cursor: String?, language: String?, tag: String?): MutableList<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        if (cursor != null) items += "cursor" to cursor
        if (language != null) items += "lang" to language
        if (tag != null) items += "tag" to tag
        return items
    }

    private fun url(path: String, query: List<Pair<String, String>>): String {
        val base = address(path)
        if (query.isEmpty()) return base
        return base + "?" + query.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }
    }

    private suspend fun perform(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: ByteArray? = null,
        contentType: String? = null,
        signed: Boolean = false,
        fresh: Boolean = false,
        timeoutSeconds: Int = 20,
    ): Response = withContext(Dispatchers.IO) {
        val token = sessionToken
        if (signed && token == null) {
            throw ApiError.Server(401, "unauthorized", null, R.string.error_sign_in_first)
        }
        val connection = try {
            URL(url(path, query)).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw ApiError.Transport(error.toString())
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutSeconds * 1000
            connection.readTimeout = timeoutSeconds * 1000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", AppLanguage.current.acceptLanguage)
            connection.setRequestProperty("User-Agent", "Kite/${BuildConfig.VERSION_NAME} (Android)")
            if (fresh || signed) {
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
            }
            if (token != null && signed) {
                connection.setRequestProperty("Cookie", "explore_session=$token")
                val csrf = csrfToken
                if (method != "GET" && csrf != null) connection.setRequestProperty("X-CSRF-Token", csrf)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/json")
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readBytes().decodeToString() } ?: ""
            val response = Response(status, text, connection.headerFields)
            if (status !in 200..299) throw ApiError.from(status, text)
            response
        } catch (error: ApiError) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw ApiError.TimedOut()
        } catch (_: UnknownHostException) {
            throw ApiError.Offline()
        } catch (error: IOException) {
            throw ApiError.Transport(error.toString())
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, text: String): T = try {
        ExploreJson.decodeFromString(deserializer, text)
    } catch (error: SerializationException) {
        throw ApiError.InvalidResponse(error.toString())
    } catch (error: IllegalArgumentException) {
        throw ApiError.InvalidResponse(error.toString())
    }

    private suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: List<Pair<String, String>> = emptyList(),
        signed: Boolean = false,
        fresh: Boolean = false,
    ): T = decode(deserializer, perform("GET", path, query, signed = signed, fresh = fresh).text)

    private suspend fun <T> send(
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        body: JsonObject?,
        signed: Boolean = false,
        timeoutSeconds: Int = 20,
    ): T {
        val bytes = body?.toString()?.encodeToByteArray()
        return decode(deserializer, perform(method, path, body = bytes, signed = signed, timeoutSeconds = timeoutSeconds).text)
    }

    /** Account writes answer 201 or 204 with no body worth reading. */
    private suspend fun sendEmpty(method: String, path: String, body: JsonObject?) {
        perform(method, path, body = body?.toString()?.encodeToByteArray(), signed = true)
    }

    private suspend fun startSession(path: String, body: JsonObject): Pair<User, String> {
        val response = perform("POST", path, body = body.toString().encodeToByteArray())
        val user = decode(User.serializer(), response.text)
        val token = sessionCookie(response.headers) ?: throw ApiError.InvalidResponse("no session cookie")
        return user to token
    }

    companion object {
        /** The session token from a response's Set-Cookie headers. */
        fun sessionCookie(headers: Map<String?, List<String>>): String? =
            headers.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }
                .firstOrNull { it.trimStart().startsWith("explore_session=") }
                ?.trimStart()
                ?.substringAfter("=")
                ?.substringBefore(";")
                ?.takeIf { it.isNotEmpty() }

        private fun json(vararg pairs: Pair<String, String>): JsonObject =
            JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })
    }
}
