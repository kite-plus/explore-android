package plus.kite.explore.blogs

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.delay
import plus.kite.explore.R
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.LocalScreenId
import plus.kite.explore.app.ReportTarget
import plus.kite.explore.app.Route
import plus.kite.explore.app.ShareSubject
import plus.kite.explore.app.SignInMode
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.Blog
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.PageFooter
import plus.kite.explore.design.Pill
import plus.kite.explore.design.PlaceholderBlock
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.SecondaryButton
import plus.kite.explore.design.shimmer
import plus.kite.explore.discover.languageTitle
import plus.kite.explore.feed.EntryList
import plus.kite.explore.feed.FeedModel
import plus.kite.explore.feed.MenuRow
import plus.kite.explore.search.SearchModel
import plus.kite.explore.search.SearchTerms

/** The blog directory, most recently updated first, kept while other tabs show. */
class DirectoryState {
    var blogs by mutableStateOf<List<Blog>>(emptyList())
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var phase by mutableStateOf<FeedModel.Phase>(FeedModel.Phase.Idle)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var loadMoreError by mutableStateOf<Throwable?>(null)
        private set
    val list = LazyListState()
    var query by mutableStateOf("")

    /** The server's answer to the field, blogs only, in the language the filter shows. */
    val search = SearchModel(includesPosts = false)

    /** Bumped by Try Again, so the search runs again in the screen's scope, not the button's. */
    var searchAttempt by mutableIntStateOf(0)
    private var searchListQuery: SearchModel.Query? = null
    private var searchListState = LazyListState()
    private var loadedKey: String? = null
    private var loadedAt: Instant? = null

    private fun key(client: ApiClient, language: LanguageFilter) = "${client.server}|${language.key}"

    /** Loads when missing, for another server or language, or older than ten minutes. */
    suspend fun loadIfNeeded(client: ApiClient, language: LanguageFilter) {
        val at = loadedAt
        if (loadedKey == key(client, language) && phase == FeedModel.Phase.Loaded && at != null &&
            Instant.now().epochSecond - at.epochSecond < 600
        ) {
            return
        }
        load(client, language)
    }

    suspend fun load(client: ApiClient, language: LanguageFilter, fresh: Boolean = false) {
        val key = key(client, language)
        if (loadedKey != key) {
            blogs = emptyList()
            nextCursor = null
            phase = FeedModel.Phase.Loading
        }
        try {
            val page = client.blogs(null, language.code, fresh = fresh)
            blogs = page.data
            nextCursor = page.nextCursor
            loadedKey = key
            loadedAt = Instant.now()
            loadMoreError = null
            phase = FeedModel.Phase.Loaded
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            phase = if (blogs.isEmpty()) FeedModel.Phase.Failed(error) else FeedModel.Phase.Loaded
        }
    }

    /** The scroll position for the search results on screen, fresh for each result set so it starts at the top. */
    fun searchList(query: SearchModel.Query?): LazyListState {
        if (query != searchListQuery) {
            searchListQuery = query
            searchListState = LazyListState()
        }
        return searchListState
    }

    suspend fun loadMore(client: ApiClient, language: LanguageFilter) {
        val cursor = nextCursor ?: return
        if (isLoadingMore || phase != FeedModel.Phase.Loaded) return
        isLoadingMore = true
        loadMoreError = null
        try {
            val page = client.blogs(cursor, language.code)
            val known = blogs.map { it.host }.toSet()
            blogs = blogs + page.data.filter { it.host !in known }
            nextCursor = page.nextCursor
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            loadMoreError = error
        } finally {
            isLoadingMore = false
        }
    }
}

@Composable
fun BlogsScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    val state = app.directory
    val language = LanguageFilter.of(app.prefs.blogsLanguage)
    val trimmed = state.query.trim()
    val search = state.search
    val current = SearchModel.Query(trimmed, language.code)
    LaunchedEffect(language, app.server) { state.loadIfNeeded(app.client, language) }
    LaunchedEffect(trimmed, language, app.server, state.searchAttempt) {
        if (trimmed.isEmpty()) {
            search.reset()
            return@LaunchedEffect
        }
        // Waits for a pause in typing; the next keystroke cancels this.
        delay(300)
        search.search(current, app.client)
        if (search.phase == SearchModel.Phase.Unsupported) app.blogIndex.loadIfNeeded(app.client)
    }
    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_blogs), style = ExploreType.largeTitle, color = colors.label)
                Text(stringResource(R.string.blogs_subtitle), style = ExploreType.footnote, color = colors.secondaryLabel)
            }
            LanguageMenu(language) { app.prefs.blogsLanguage = it.key }
            IconButton(onClick = { app.sheet = AppSheet.Submit() }) { Icon(Icons.Rounded.Add, stringResource(R.string.submit_title), tint = colors.label) }
        }
        SearchField(
            state.query,
            { state.query = it },
            stringResource(R.string.blogs_search_prompt),
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyColumn(
            state = if (trimmed.isEmpty()) state.list else state.searchList(search.query),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (trimmed.isNotEmpty()) {
                val deviceMatches = if (search.phase == SearchModel.Phase.Unsupported) app.blogIndex.matches(trimmed) else emptyList()
                searchResults(search, current, deviceMatches) { state.searchAttempt++ }
            } else {
                when (val phase = state.phase) {
                    FeedModel.Phase.Idle, FeedModel.Phase.Loading -> items(5) { BlogPlaceholder() }
                    is FeedModel.Phase.Failed -> item {
                        LoadFailed(app.message(phase.error)) { state.load(app.client, language, fresh = true) }
                    }
                    FeedModel.Phase.Loaded -> if (state.blogs.isEmpty()) {
                        item {
                            EmptyState(Icons.Rounded.GridView, stringResource(R.string.blogs_none_title), stringResource(R.string.blogs_none_description)) {
                                PrimaryButton(onClick = { app.sheet = AppSheet.Submit() }) { Text(stringResource(R.string.submit_title)) }
                            }
                        }
                    } else {
                        items(state.blogs, key = { it.host }) { BlogCard(it) }
                        item(key = "footer") {
                            PageFooter(
                                state.isLoadingMore, state.loadMoreError?.let { app.message(it) }, state.nextCursor != null,
                                state.blogs.size, stringResource(R.string.list_end_blogs),
                            ) { state.loadMore(app.client, language) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The server searches every blog in the chosen language; one from before search leaves it to
 * the device, over every blog.
 */
private fun LazyListScope.searchResults(search: SearchModel, current: SearchModel.Query, deviceMatches: List<Blog>, retry: () -> Unit) {
    val phase = search.phase
    when {
        phase == SearchModel.Phase.Unsupported -> if (deviceMatches.isEmpty()) {
            item(key = "search-empty") { BlogSearchEmpty(current.text) }
        } else {
            items(deviceMatches, key = { it.host }) { BlogCard(it) }
        }
        phase is SearchModel.Phase.Failed -> item(key = "search-failed") {
            val app = LocalApp.current
            LoadFailed(app.message(phase.error)) { retry() }
        }
        search.blogs.isNotEmpty() -> {
            val words = SearchTerms.words(search.query?.text.orEmpty())
            items(search.blogs, key = { it.host }) { BlogCard(it, words) }
            item(key = "search-footer") {
                val app = LocalApp.current
                PageFooter(
                    search.isLoadingMoreBlogs, search.blogPageError?.let { app.message(it) }, search.blogCursor != null,
                    search.blogs.size, null,
                ) { search.loadMoreBlogs(app.client) }
            }
        }
        search.query != current -> items(3, key = { "search-placeholder-$it" }) { BlogPlaceholder() }
        else -> item(key = "search-none") {
            EmptyState(
                Icons.AutoMirrored.Rounded.ManageSearch,
                stringResource(R.string.search_no_results_title, current.text),
                stringResource(R.string.search_no_results_description),
            )
        }
    }
}

/** A search field shaped like the system's on iOS: a gray capsule with a clear button. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val colors = Explore.colors
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .let { if (focusRequester != null) it.then(Modifier.focusRequester(focusRequester)) else it },
        placeholder = { Text(placeholder, style = ExploreType.body, color = colors.secondaryLabel) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.secondaryLabel) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Clear, stringResource(R.string.action_clear), tint = colors.secondaryLabel) }
            }
        },
        singleLine = true,
        textStyle = ExploreType.body,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search, autoCorrectEnabled = false),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.fill,
            unfocusedContainerColor = colors.fill,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

/** The blog language menu, filled while it narrows the list. */
@Composable
fun LanguageMenu(language: LanguageFilter, onChange: (LanguageFilter) -> Unit) {
    val colors = Explore.colors
    var shown by remember { mutableStateOf(false) }
    Box {
        val label = stringResource(R.string.filter_blog_language)
        if (language == LanguageFilter.All) {
            IconButton(onClick = { shown = true }) { Icon(Icons.Rounded.FilterList, label, tint = colors.label) }
        } else {
            FilledIconButton(
                onClick = { shown = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent, contentColor = colors.background),
            ) { Icon(Icons.Rounded.FilterList, label) }
        }
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }) {
            Text(label, style = ExploreType.footnote, color = colors.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            for (option in LanguageFilter.entries) {
                DropdownMenuItem(
                    text = { Text(languageTitle(option)) },
                    leadingIcon = { RadioButton(selected = option == language, onClick = null) },
                    onClick = {
                        onChange(option)
                        shown = false
                    },
                )
            }
        }
    }
}

/** Shown when a search finds nothing: still loading, failed, or no match. */
@Composable
fun BlogSearchEmpty(query: String) {
    val app = LocalApp.current
    val index = app.blogIndex
    val error = index.error
    when {
        index.isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        index.blogs.isEmpty() && error != null -> LoadFailed(app.message(error)) { index.loadIfNeeded(app.client) }
        else -> EmptyState(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.search_no_results_title, query), stringResource(R.string.search_no_results_description))
    }
}

/**
 * A blog in the directory. The card opens the blog's page; a long press shows its actions.
 * A search's words are marked in the name and description.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlogCard(blog: Blog, highlight: List<String> = emptyList()) {
    val app = LocalApp.current
    val colors = Explore.colors
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .combinedClickable(onLongClick = { menu = true }) { app.navigate(Route.BlogPage(blog.ref)) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlogAvatar(blog.host, blog.name, 48.dp)
                Column(Modifier.weight(1f).padding(end = 110.dp)) {
                    Text(SearchTerms.marked(blog.name, highlight, colors.mark), style = ExploreType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Formatting.displayHost(blog.host), style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (blog.about.isNotEmpty()) {
                Text(
                    SearchTerms.marked(SearchTerms.snippet(blog.about, highlight), highlight, colors.mark),
                    style = ExploreType.subheadline,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BlogFacts(blog)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FollowButton(blog.ref, compact = true)
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                BlogActions(blog.ref, blog.feedUrl) { menu = false }
            }
        }
    }
}

@Composable
fun BlogFacts(blog: Blog) {
    val context = LocalContext.current
    val colors = Explore.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Formatting.languageName(blog.language)?.let { Pill(it, Icons.Rounded.ChatBubbleOutline) }
        Formatting.generatorName(context, blog.generator)?.let { Pill(it, Icons.Rounded.Inventory2) }
        Spacer(Modifier.weight(1f))
        blog.lastPublishedAt?.let {
            Text(stringResource(R.string.blog_updated, Formatting.relative(context, it)), style = ExploreType.caption, color = colors.secondaryLabel, maxLines = 1)
        }
    }
}

/** Follow or unfollow: a main action until followed, then a quiet one. */
@Composable
fun FollowButton(blog: BlogRef, compact: Boolean = false, quiet: Boolean = false) {
    val app = LocalApp.current
    val following = app.isFollowing(blog.host)
    val pending = blog.host in app.pendingFollows
    val label: @Composable () -> Unit = {
        Icon(if (following) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(18.dp))
        Text(
            " " + stringResource(if (following) R.string.follow_state_following else R.string.action_follow),
            style = if (compact) ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold) else ExploreType.body.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    if (following || quiet) {
        SecondaryButton(onClick = { app.toggleFollow(blog) }, enabled = !pending, large = !compact) { label() }
    } else {
        PrimaryButton(onClick = { app.toggleFollow(blog) }, enabled = !pending, large = !compact) { label() }
    }
}

/** Actions for a blog, shared by its menus. */
@Composable
fun BlogActions(blog: BlogRef, feedUrl: String?, dismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    fun act(action: () -> Unit) {
        dismiss()
        action()
    }
    MenuRow(stringResource(R.string.blog_visit), Icons.AutoMirrored.Rounded.OpenInNew) { act { app.openLink(blog.siteUrl, app.sourceTag) } }
    val following = app.isFollowing(blog.host)
    MenuRow(
        stringResource(if (following) R.string.action_unfollow else R.string.action_follow),
        if (following) Icons.Rounded.HeartBroken else Icons.Rounded.FavoriteBorder,
    ) { act { app.toggleFollow(blog) } }
    if (!feedUrl.isNullOrEmpty()) {
        HorizontalDivider()
        MenuRow(stringResource(R.string.blog_copy_feed), Icons.Rounded.RssFeed) {
            act {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(blog.name, feedUrl))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) app.showToast(app.string(R.string.toast_feed_copied), Icons.Rounded.Link)
            }
        }
    }
    HorizontalDivider()
    MenuRow(stringResource(R.string.action_share), Icons.Rounded.Share) { act { app.sheet = AppSheet.Share(ShareSubject.Blog(blog)) } }
    MenuRow(stringResource(R.string.blog_claim), Icons.Rounded.Verified) {
        act { app.sheet = if (app.isSignedIn) AppSheet.Claim(blog) else AppSheet.SignIn(SignInMode.SignIn) }
    }
    MenuRow(stringResource(R.string.action_report), Icons.Rounded.Flag, destructive = true) {
        act {
            val target = ReportTarget(blog.host, blog.name, null, null)
            app.sheet = if (app.isSignedIn) AppSheet.Report(target) else AppSheet.SignIn(SignInMode.SignIn)
        }
    }
}

@Composable
private fun BlogPlaceholder() {
    val colors = Explore.colors
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .padding(16.dp)
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.fill))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaceholderBlock(140.dp, 16.dp)
                PlaceholderBlock(110.dp, 13.dp)
            }
        }
        PlaceholderBlock(260.dp, 13.dp)
        PlaceholderBlock(200.dp, 13.dp)
    }
}

/** A blog page's posts and how far they were scrolled. */
class BlogPageState(host: String) {
    val feed = FeedModel(FeedModel.Source.Blog(host))
    val list = LazyListState()
}

/** A blog's page: who it is, how to visit and follow it, and the posts in its feed. */
@Composable
fun BlogScreen(ref: BlogRef) {
    val app = LocalApp.current
    val id = LocalScreenId.current
    val page = app.screenState(id) { BlogPageState(ref.host) }
    val feed = page.feed
    val list = page.list
    val name = feed.blog?.name ?: ref.name
    val titleVisible by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    LaunchedEffect(app.server) { feed.refreshIfNeeded(app.client) }
    PushedScreen(
        title = name,
        titleVisible = titleVisible,
        actions = {
            IconButton(onClick = { app.sheet = AppSheet.Share(ShareSubject.Blog(feed.blog?.ref ?: ref)) }) {
                Icon(Icons.Rounded.Share, stringResource(R.string.action_share))
            }
        },
    ) { padding ->
        EntryList(
            feed = feed,
            modifier = Modifier.padding(padding),
            showsBlog = false,
            contextBlog = feed.blog?.ref ?: ref,
            endText = null,
            state = list,
            onRefresh = { feed.load(app.client, fresh = true) },
            header = {
                item(key = "blog-header") { BlogHeader(ref, feed.blog) }
                if (feed.entries.isNotEmpty()) {
                    item(key = "recent") {
                        Text(
                            stringResource(R.string.blog_recent_posts),
                            style = ExploreType.title3.copy(fontWeight = FontWeight.Bold),
                            color = Explore.colors.label,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                }
            },
        ) {
            EmptyState(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.blog_no_posts_title), stringResource(R.string.blog_no_posts_description))
        }
    }
}

@Composable
private fun BlogHeader(ref: BlogRef, blog: Blog?) {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    val name = blog?.name ?: ref.name
    var expanded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(106.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
            BlogAvatar(ref.host, name, 92.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = ExploreType.title, color = colors.label, textAlign = TextAlign.Center)
            Row(
                modifier = Modifier.clickable { app.openLink(blog?.siteUrl ?: ref.siteUrl, app.sourceTag) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Link, null, Modifier.size(16.dp), tint = colors.secondaryLabel)
                Text(Formatting.displayHost(ref.host), style = ExploreType.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.secondaryLabel)
            }
        }
        val about = blog?.about.orEmpty()
        if (about.isNotEmpty()) {
            Text(
                about,
                style = ExploreType.callout,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(onClick = { app.openLink(blog?.siteUrl ?: ref.siteUrl, app.sourceTag) }, large = true) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                Text(" " + stringResource(R.string.blog_visit), style = ExploreType.body.copy(fontWeight = FontWeight.SemiBold))
            }
            FollowButton(blog?.ref ?: ref, quiet = true)
            Box {
                SecondaryButton(onClick = { menu = true }, large = true) {
                    Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.action_more), Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    BlogActions(blog?.ref ?: ref, blog?.feedUrl) { menu = false }
                }
            }
        }
        if (blog != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Formatting.languageName(blog.language)?.let { Pill(it, Icons.Rounded.ChatBubbleOutline) }
                Formatting.generatorName(context, blog.generator)?.let { Pill(it, Icons.Rounded.Inventory2) }
                blog.lastPublishedAt?.let { Pill(stringResource(R.string.blog_updated, Formatting.relative(context, it)), Icons.Rounded.Schedule) }
            }
        }
    }
}
