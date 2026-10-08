package plus.kite.explore.app

import java.net.URI
import java.net.URLDecoder
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.Notice

enum class AppTab { Discover, Blogs, Me }

/** The streams Discover switches between, as the website's tabs do. */
enum class DiscoverStream { Latest, Recommended, Following }

/** The blog languages a list can be narrowed to, as on the website. */
enum class LanguageFilter(val key: String) {
    All("all"), Zh("zh"), En("en");

    /** The value of the API's lang parameter. */
    val code: String? get() = if (this == All) null else key

    companion object {
        fun of(key: String): LanguageFilter = entries.firstOrNull { it.key == key } ?: All
    }
}

/** Screens pushed onto a tab's stack. */
sealed interface Route {
    data object Root : Route
    data class BlogPage(val ref: BlogRef) : Route
    data class TopicPage(val slug: String) : Route
    data object Search : Route

    /** A notice's page; preview is what the list showed, for while it loads. */
    data class NoticePage(val id: String, val preview: Notice? = null) : Route
    data class SubmissionPage(val id: String) : Route
    data object Submissions : Route
    data object Subscriptions : Route
    data object OwnedBlogs : Route
    data object Server : Route
    data object About : Route
}

/** A route on a stack, with an id of its own so the same page can be pushed twice. */
data class Screen(val route: Route, val id: Long = nextId()) {
    companion object {
        private var counter = 0L

        @Synchronized
        private fun nextId(): Long = ++counter
    }
}

enum class SignInMode { SignIn, Register }

/** What a report is about: a whole blog or one of its posts. */
data class ReportTarget(val blogHost: String, val blogName: String, val entryId: String?, val entryTitle: String?)

/** What the share sheet makes a card for. */
sealed interface ShareSubject {
    data class Post(val entry: Entry, val blog: BlogRef?) : ShareSubject
    data class Blog(val blog: BlogRef) : ShareSubject
}

/** Sheets that any screen can ask for. */
sealed interface AppSheet {
    data class SignIn(val mode: SignInMode) : AppSheet

    /** The submit form, with a blog's addresses filled in when they are known. */
    data class Submit(val site: String = "", val feed: String = "") : AppSheet
    data class Report(val target: ReportTarget) : AppSheet
    data class Claim(val blog: BlogRef) : AppSheet
    data class Share(val subject: ShareSubject) : AppSheet
    data object EditName : AppSheet

    /** signedInWith is the temporary password just used to sign in, so only the new one is asked for. */
    data class ChangePassword(val signedInWith: String? = null) : AppSheet
    data object DeleteAccount : AppSheet
    data object Filter : AppSheet
}

/** Where a link into the app leads. */
sealed interface DeepLink {
    data class OpenPost(val url: String) : DeepLink
    data class Blog(val host: String) : DeepLink
    data object BlogsTab : DeepLink
    data class Topic(val slug: String) : DeepLink
    data class Submission(val id: String) : DeepLink
    data class Submit(val site: String, val feed: String) : DeepLink
    data object SignIn : DeepLink
    data class Stream(val stream: DiscoverStream) : DeepLink
    data object MeTab : DeepLink
    data object Search : DeepLink
    data object About : DeepLink
    data class NoticePage(val id: String) : DeepLink
    data object DiscoverTab : DeepLink

    companion object {
        /**
         * Reads explore:// links and website addresses with the same paths: /blogs/{host},
         * /topics/{slug}, /submissions/{id}, /notices/{id}, /submit (with the website's site and
         * feed query), /sign-in, the streams /recommended and /following, and the tab names.
         */
        fun parse(address: String): DeepLink {
            val uri = try {
                URI(address)
            } catch (_: Exception) {
                return DiscoverTab
            }
            val parts = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }.map { decode(it) }.toMutableList()
            if (uri.scheme == "explore") uri.host?.let { parts.add(0, it) }
            if (parts.firstOrNull() == "en") parts.removeAt(0)
            val query = (uri.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate {
                decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
            }
            val first = parts.getOrNull(0)
            val second = parts.getOrNull(1)
            return when {
                first == "open" -> {
                    val target = query["url"]
                    val scheme = target?.substringBefore(':', "")?.lowercase()
                    if (target != null && (scheme == "http" || scheme == "https")) OpenPost(target) else DiscoverTab
                }
                first == "blogs" && second != null -> Blog(second.lowercase())
                first == "blogs" -> BlogsTab
                first == "topics" && second != null -> Topic(second)
                first == "submissions" && second != null -> Submission(second)
                first == "submit" -> Submit(query["site"] ?: "", query["feed"] ?: "")
                first == "sign-in" || first == "login" -> SignIn
                first == null -> Stream(DiscoverStream.Latest)
                first == "recommended" -> Stream(DiscoverStream.Recommended)
                first == "following" -> Stream(DiscoverStream.Following)
                first == "me" || first == "account" -> MeTab
                first == "search" -> Search
                first == "about" -> About
                first == "notices" && second != null -> NoticePage(second)
                else -> DiscoverTab
            }
        }

        private fun decode(text: String): String = try {
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            text
        }
    }
}
