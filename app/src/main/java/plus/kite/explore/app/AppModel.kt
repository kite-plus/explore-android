package plus.kite.explore.app

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.kite.explore.BuildConfig
import plus.kite.explore.R
import plus.kite.explore.blogs.DirectoryState
import plus.kite.explore.discover.DiscoverState
import plus.kite.explore.core.Formatting
import plus.kite.explore.core.LinkOpener
import plus.kite.explore.core.PendingSignInStore
import plus.kite.explore.core.Preferences
import plus.kite.explore.core.TokenStore
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.AppSignIn
import plus.kite.explore.data.AuthOptions
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.LinkState
import plus.kite.explore.data.LinkStatus
import plus.kite.explore.data.PendingSignIns
import plus.kite.explore.data.Topic
import plus.kite.explore.data.User
import plus.kite.explore.data.isCancellation
import plus.kite.explore.data.readableMessage
import plus.kite.explore.design.Toast
import java.time.Instant

val LocalApp = staticCompositionLocalOf<AppModel> { error("No AppModel provided") }

/** A post on its way to the author's site. */
data class Handoff(val url: String, val title: String, val blog: BlogRef?, val entry: Entry, val id: Long = System.nanoTime())

/**
 * App-wide state: the server, the account, the tag list, follows and link checks, the tab
 * stacks and the sheet on screen. Screens keep their own lists; this holds what several share.
 * It lives as long as the process, so a rotation or a trip to a browser tab loses nothing.
 */
class AppModel(context: Context) {
    private val context: Context = context.applicationContext
    val prefs = Preferences(this.context)
    private val tokens = TokenStore(this.context)
    private val signIns = PendingSignIns(PendingSignInStore(this.context))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The activity on screen, for opening browser tabs over it. */
    var activity: WeakReference<Activity>? = null

    var tab by mutableStateOf(AppTab.Discover)
    var stream by mutableStateOf(DiscoverStream.Latest)
    var sheet by mutableStateOf<AppSheet?>(null)
    val stacks: Map<AppTab, SnapshotStateList<Screen>> =
        AppTab.entries.associateWith { mutableStateListOf(Screen(Route.Root)) }

    /** A post about to open, which the root names for a moment first. */
    var handoff by mutableStateOf<Handoff?>(null)
        private set

    /** Counts taps on Discover's tab while it shows, so the current stream scrolls to the top. */
    var discoverRetaps by mutableIntStateOf(0)
        private set

    /** Every blog, for searching a server without search; loaded the first time someone searches one. */
    val blogIndex = BlogIndex()

    /** Discover's streams and the directory stay loaded and scrolled while other tabs show. */
    val discover = DiscoverState()
    val directory = DirectoryState()
    var toast by mutableStateOf<Toast?>(null)
        private set

    var client by mutableStateOf(newClient(storedServer()))
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var isRestoringSession by mutableStateOf(false)
        private set
    private var restoringToken: String? = null
    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set
    var notice by mutableStateOf("")
        private set
    var registrationEnabled by mutableStateOf(true)
        private set

    /** How the server signs readers in; null until it answers, which reads as passwords only. */
    var authOptions by mutableStateOf<AuthOptions?>(null)
        private set

    /** True while a code from Kite Plus sign-in is being traded for a session. */
    var isFinishingKitePlus by mutableStateOf(false)
        private set

    /** Why the last Kite Plus sign-in failed, for the sign-in sheet to show. */
    var kitePlusError by mutableStateOf<String?>(null)
    var followedHosts by mutableStateOf<Set<String>>(emptySet())
        private set
    var pendingFollows by mutableStateOf<Set<String>>(emptySet())
        private set
    val linkStates = mutableStateMapOf<String, LinkState>()
    var checkingLinks by mutableStateOf<Set<String>>(emptySet())
        private set
    var submissionIds by mutableStateOf(prefs.submissions(key(client.server)))
        private set

    /** Changes whenever the server or the account changes, so lists reload. */
    var generation by mutableIntStateOf(0)
        private set

    /** Addresses of the posts opened on this device. They never leave it. */
    var readPosts by mutableStateOf(prefs.readPosts.toSet())
        private set

    private var toastJob: Job? = null
    private var isLoadingTopics = false
    private var siteConfigLoadedAt: Instant? = null

    val server: String get() = client.server
    val isSignedIn: Boolean get() = user != null
    val isDefaultServer: Boolean get() = server == DEFAULT_SERVER

    /** The identity provider the server signs in through, such as Kite Plus, if it has one. */
    val kitePlus: AuthOptions.Oidc? get() = authOptions?.oidc

    /** New accounts come from Kite Plus on a server that has it, so the app offers no sign-up there. */
    val allowsRegistration: Boolean get() = registrationEnabled && kitePlus == null

    val allowsPassword: Boolean get() = authOptions?.password != false

    /** The utm_source added to links that leave the app: the server's host, as the website does. */
    val sourceTag: String get() = client.host

    private fun newClient(server: String) = ApiClient(server, tokens.token(key(server)))

    private fun storedServer(): String = prefs.server?.takeIf { Formatting.isWeb(it) } ?: DEFAULT_SERVER

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Loads what every screen needs once: the tag list, the site notice and the session kept from last time. */
    fun bootstrap() {
        scope.launch { loadTopics() }
        scope.launch { loadSiteConfig() }
        scope.launch { loadAuthOptions() }
        scope.launch { restoreSession() }
    }

    // Topics and site settings

    suspend fun loadTopics() {
        if (topics.isNotEmpty() || isLoadingTopics) return
        isLoadingTopics = true
        try {
            topics = client.topics()
        } catch (_: Exception) {
            // The tags come back on the next try.
        } finally {
            isLoadingTopics = false
        }
    }

    suspend fun loadSiteConfig() {
        val config = try {
            client.siteConfig()
        } catch (_: Exception) {
            return
        }
        notice = config.notice.trim()
        registrationEnabled = config.registrationEnabled
        siteConfigLoadedAt = Instant.now()
    }

    suspend fun loadAuthOptions() {
        val client = client
        val options = try {
            client.authOptions()
        } catch (_: Exception) {
            return
        }
        if (client === this.client) authOptions = options
    }

    /** Catches up when the app comes back: whatever failed at launch, and a site notice older than ten minutes. */
    fun catchUp() {
        scope.launch { loadTopics() }
        scope.launch { restoreSession() }
        if (authOptions == null) scope.launch { loadAuthOptions() }
        val loadedAt = siteConfigLoadedAt
        if (loadedAt == null || Instant.now().epochSecond - loadedAt.epochSecond > 600) {
            scope.launch { loadSiteConfig() }
        }
    }

    fun topic(slug: String): Topic? = topics.firstOrNull { it.slug == slug }

    fun topicName(slug: String): String? = topic(slug)?.name(plus.kite.explore.data.AppLanguage.current)

    // Session

    suspend fun restoreSession() {
        val client = client
        val token = client.sessionToken ?: return
        if (user != null || restoringToken == token) return
        restoringToken = token
        isRestoringSession = true
        // The answer only counts for the server and sign-in it was asked about.
        fun isCurrent() = client === this.client && client.sessionToken == token
        try {
            val me = client.me()
            if (!isCurrent()) return
            apply(me)
            loadFollows()
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            if (error is ApiError && error.isUnauthorized && isCurrent()) endSession()
            // Offline: keep the token and try again when the app comes back.
        } finally {
            if (restoringToken == token) {
                restoringToken = null
                isRestoringSession = false
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        val (user, token) = client.signIn(email, password)
        startSession(user, token)
        loadFollows()
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val (user, token) = client.register(email, password, displayName)
        startSession(user, token)
    }

    /**
     * Opens the server's Kite Plus sign-in in a browser tab. The browser keeps the provider's
     * sign-in, so signing in again returns to the same account; switchAccount asks the provider
     * to offer another one.
     */
    fun startKitePlusSignIn(switchAccount: Boolean) {
        kitePlusError = null
        val url = signIns.start(server, switchAccount)
        // The start address carries only the PKCE challenge; tests read it to finish signing in on another browser.
        if (BuildConfig.DEBUG) Log.d("KiteSignIn", "start $url")
        LinkOpener.openSignIn(activityOrContext(), url)
    }

    /**
     * Takes an address that came into the app and, when it is a Kite Plus sign-in callback,
     * finishes the pending sign-in with it. Returns whether the address was a callback.
     */
    fun handleSignInCallback(address: String): Boolean {
        val callback = AppSignIn.parseCallback(address) ?: return false
        when (val outcome = signIns.resolve(callback, server)) {
            is PendingSignIns.Outcome.Exchange -> scope.launch { finishKitePlusSignIn(outcome.code, outcome.verifier) }
            is PendingSignIns.Outcome.Failed -> AppSignIn.failureMessage(outcome.reason)?.let { failKitePlusSignIn(string(it)) }
            PendingSignIns.Outcome.Ignored -> Unit
        }
        return true
    }

    private suspend fun finishKitePlusSignIn(code: String, verifier: String) {
        val client = client
        isFinishingKitePlus = true
        kitePlusError = null
        try {
            val (user, token) = client.exchange(code, verifier)
            if (client !== this.client) return
            startSession(user, token)
            if (sheet is AppSheet.SignIn) sheet = null
            showToast(string(R.string.toast_signed_in), Icons.Rounded.HowToReg)
            loadFollows()
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            val code = (error as? ApiError)?.code
            failKitePlusSignIn(if (code == "invalid_grant") string(R.string.kite_plus_error_expired) else message(error))
        } finally {
            isFinishingKitePlus = false
        }
    }

    /** Shows why signing in failed on the sign-in sheet, opening it when the app came back without it. */
    private fun failKitePlusSignIn(message: String) {
        kitePlusError = message
        if (sheet !is AppSheet.SignIn) sheet = AppSheet.SignIn(SignInMode.SignIn)
    }

    fun signOut() {
        scope.launch {
            try {
                client.signOut()
            } catch (_: Exception) {
                // Signed out on this device either way.
            }
            endSession()
            showToast(string(R.string.toast_signed_out), Icons.AutoMirrored.Rounded.Logout)
        }
    }

    suspend fun updateName(name: String) {
        apply(client.updateName(name))
    }

    suspend fun changePassword(current: String, new: String) {
        client.changePassword(current, new)
        user = user?.copy(temporaryPassword = false)
    }

    suspend fun deleteAccount() {
        client.deleteAccount()
        endSession()
    }

    private fun startSession(user: User, token: String) {
        tokens.setToken(key(server), token)
        client.sessionToken = token
        apply(user)
        generation++
    }

    private fun apply(user: User) {
        this.user = user
        client.csrfToken = user.csrfToken
    }

    private fun endSession() {
        tokens.setToken(key(server), null)
        client.sessionToken = null
        client.csrfToken = null
        user = null
        followedHosts = emptySet()
        generation++
    }

    /** Signs out locally when the server no longer knows the session. */
    fun handleAuthError(error: Throwable) {
        if (error is ApiError && error.isUnauthorized && isSignedIn) {
            endSession()
            sheet = AppSheet.SignIn(SignInMode.SignIn)
        }
    }

    // Follows

    suspend fun loadFollows() {
        if (!isSignedIn) return
        try {
            followedHosts = client.subscriptions().map { it.host }.toSet()
        } catch (_: Exception) {
            // The follows load again with the next list.
        }
    }

    fun isFollowing(host: String): Boolean = host in followedHosts

    fun replaceFollowedHosts(hosts: Set<String>) {
        followedHosts = hosts
    }

    /** Follows or unfollows a blog, asking the reader to sign in first. */
    fun toggleFollow(blog: BlogRef, onDone: ((String) -> Unit)? = null) {
        if (!isSignedIn) {
            sheet = AppSheet.SignIn(SignInMode.SignIn)
            return
        }
        val host = blog.host
        if (host in pendingFollows) return
        val follow = host !in followedHosts
        pendingFollows = pendingFollows + host
        followedHosts = if (follow) followedHosts + host else followedHosts - host
        scope.launch {
            try {
                if (follow) client.subscribe(host) else client.unsubscribe(host)
                val message = string(if (follow) R.string.toast_following else R.string.toast_unfollowed, blog.name)
                if (onDone != null) onDone(message) else showToast(message, if (follow) Icons.Rounded.Favorite else Icons.Rounded.HeartBroken)
                generation++
            } catch (error: Exception) {
                followedHosts = if (follow) followedHosts - host else followedHosts + host
                handleAuthError(error)
                if (onDone != null) onDone(error.readableMessage(context)) else showError(error)
            } finally {
                pendingFollows = pendingFollows - host
            }
        }
    }

    // Link checks

    fun linkStatus(entry: Entry): Pair<LinkStatus, Instant?> {
        val state = linkStates[entry.id] ?: return entry.linkStatus to entry.linkCheckedAt
        return state.linkStatus to state.linkCheckedAt
    }

    /** Asks Explore to check a post's link now and waits for the answer. */
    fun checkLink(entry: Entry) {
        if (entry.id in checkingLinks) return
        checkingLinks = checkingLinks + entry.id
        scope.launch {
            try {
                var state = client.checkLink(entry.id)
                var polls = 0
                while (state.checking && polls < 12) {
                    delay(1500)
                    state = client.linkState(entry.id)
                    polls++
                }
                linkStates[entry.id] = state
            } catch (error: Exception) {
                showError(error)
            } finally {
                checkingLinks = checkingLinks - entry.id
            }
        }
    }

    // Submissions kept on this device

    fun rememberSubmission(id: String) {
        submissionIds = listOf(id) + submissionIds.filter { it != id }
        prefs.setSubmissions(key(server), submissionIds)
    }

    fun forgetSubmission(id: String) {
        submissionIds = submissionIds.filter { it != id }
        prefs.setSubmissions(key(server), submissionIds)
    }

    // Posts read on this device

    fun isRead(entry: Entry): Boolean = entry.url in readPosts

    /** Remembers that a post was opened, unless the reader turned that off. */
    fun markRead(url: String) {
        if (!prefs.dimsReadPosts || url in readPosts) return
        val list = (prefs.readPosts + url).takeLast(READ_LIMIT)
        prefs.readPosts = list
        readPosts = list.toSet()
    }

    fun forgetReadPosts() {
        prefs.readPosts = emptyList()
        readPosts = emptySet()
    }

    // Server

    fun switchServer(url: String) {
        prefs.server = url
        client = newClient(url)
        user = null
        topics = emptyList()
        notice = ""
        authOptions = null
        kitePlusError = null
        signIns.cancel()
        followedHosts = emptySet()
        linkStates.clear()
        submissionIds = prefs.submissions(key(url))
        generation++
        bootstrap()
        showToast(string(R.string.toast_connected, client.host), Icons.Rounded.Dns)
    }

    // Navigation

    fun navigate(route: Route) {
        stacks.getValue(tab).add(Screen(route))
    }

    fun show(route: Route, inTab: AppTab) {
        tab = inTab
        stacks.getValue(inTab).add(Screen(route))
    }

    fun back(): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return false
        screenStates.remove(stack.removeAt(stack.lastIndex).id)
        return true
    }

    /**
     * State a pushed screen keeps while it is on its stack, such as a blog's posts and how far
     * they were scrolled, so a trip to another tab and back loses nothing, as on iOS.
     */
    private val screenStates = HashMap<Long, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> screenState(id: Long, create: () -> T): T = screenStates.getOrPut(id, create) as T

    /** The bottom bar's choice; a tap on the current tab goes back to its first screen. */
    fun select(newTab: AppTab) {
        if (newTab == tab) {
            val stack = stacks.getValue(newTab)
            if (stack.size > 1) {
                stack.drop(1).forEach { screenStates.remove(it.id) }
                stack.removeRange(1, stack.size)
            } else if (newTab == AppTab.Discover) {
                discoverRetaps++
            }
        }
        tab = newTab
    }

    /** Shows a blog's page on the current tab. */
    fun showBlog(blog: BlogRef) = navigate(Route.BlogPage(blog))

    fun open(link: DeepLink) {
        when (link) {
            is DeepLink.OpenPost -> {
                markRead(link.url)
                openLink(link.url, source = sourceTag)
            }
            is DeepLink.Blog -> show(Route.BlogPage(BlogRef(link.host, link.host, "https://${link.host}/", "")), AppTab.Blogs)
            DeepLink.BlogsTab -> tab = AppTab.Blogs
            is DeepLink.Topic -> show(Route.TopicPage(link.slug), AppTab.Discover)
            is DeepLink.Submission -> show(Route.SubmissionPage(link.id), AppTab.Me)
            is DeepLink.Submit -> sheet = AppSheet.Submit(link.site, link.feed)
            DeepLink.SignIn -> sheet = if (isSignedIn) null else AppSheet.SignIn(SignInMode.SignIn)
            is DeepLink.Stream -> {
                tab = AppTab.Discover
                stream = link.stream
            }
            DeepLink.MeTab -> tab = AppTab.Me
            DeepLink.Search -> show(Route.Search, AppTab.Discover)
            DeepLink.About -> show(Route.About, AppTab.Me)
            is DeepLink.NoticePage -> show(Route.NoticePage(link.id), AppTab.Discover)
            DeepLink.DiscoverTab -> tab = AppTab.Discover
        }
    }

    // Reading

    /**
     * Opens a post on the author's site by way of a moment that names it, as the website's
     * transition page does, unless the reader turned that off.
     */
    fun openPost(entry: Entry, blog: BlogRef?) {
        if (handoff != null || !Formatting.isWeb(entry.url)) return
        markRead(entry.url)
        if (!prefs.showsHandoff) {
            openPostNow(entry, blog)
            return
        }
        handoff = Handoff(entry.url, entry.title, blog ?: entry.blog, entry)
    }

    fun openPostNow(entry: Entry, blog: BlogRef?) {
        val owner = blog ?: entry.blog
        val menu = LinkOpener.PostMenu(entry, owner, owner?.let { isFollowing(it.host) } ?: false, isSignedIn)
        LinkOpener.open(activityOrContext(), entry.url, sourceTag, prefs.openInBrowser, menu)
    }

    fun endHandoff(id: Long) {
        if (handoff?.id == id) handoff = null
    }

    /** Opens a page that leaves Explore, in a browser tab or the browser. */
    fun openLink(url: String, source: String? = null) {
        LinkOpener.open(activityOrContext(), url, source, prefs.openInBrowser)
    }

    fun openInBrowser(url: String, source: String? = null) {
        LinkOpener.openInBrowser(activityOrContext(), url, source)
    }

    private fun activityOrContext(): Context = activity?.get() ?: context

    // Toasts

    fun showToast(message: String, icon: ImageVector = Icons.Rounded.CheckCircle, isError: Boolean = false) {
        toastJob?.cancel()
        val next = Toast(message, icon, isError)
        toast = next
        toastJob = scope.launch {
            delay(2600)
            if (toast?.id == next.id) toast = null
        }
    }

    fun showError(error: Throwable) {
        if (error.isCancellation) return
        showToast(error.readableMessage(context), Icons.Rounded.Warning, isError = true)
    }

    fun dismissToast() {
        toastJob?.cancel()
        toast = null
    }

    fun message(error: Throwable): String = error.readableMessage(context)

    companion object {
        const val DEFAULT_SERVER = "https://explore.kite.plus"

        /** The oldest reads are forgotten past this many. */
        const val READ_LIMIT = 2000

        fun key(server: String): String = server.trimEnd('/')
    }
}
