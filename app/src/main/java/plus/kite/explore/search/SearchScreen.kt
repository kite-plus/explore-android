package plus.kite.explore.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.kite.explore.R
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.LocalScreenId
import plus.kite.explore.app.Route
import plus.kite.explore.blogs.BlogActions
import plus.kite.explore.blogs.BlogSearchEmpty
import plus.kite.explore.blogs.SearchField
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.AppLanguage
import plus.kite.explore.data.Blog
import plus.kite.explore.data.Topic
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.EntryPlaceholder
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.PageFooter
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.SectionTitle
import plus.kite.explore.design.TopicStyle
import plus.kite.explore.discover.languageTitle
import plus.kite.explore.feed.EntryList
import plus.kite.explore.feed.EntryRow
import plus.kite.explore.feed.FeedModel

/** What Search keeps while it is on its stack: the field, the results and how far they were scrolled. */
class SearchPageState {
    var query by mutableStateOf("")
    val model = SearchModel()
    var recent by mutableStateOf<List<Blog>>(emptyList())
    var focused = false

    /** Bumped by Try Again, so the screen's search runs again in its own scope, not the button's. */
    var attempt by mutableIntStateOf(0)
    private val browseList = LazyListState()
    private var shownQuery: SearchModel.Query? = null
    private var shownLists = ResultLists()

    class ResultLists {
        val page = LazyListState()
        val strip = LazyListState()
    }

    /**
     * The scroll positions for the results on screen, fresh for each result set so new results
     * start at the top and at the first blog. An offset kept from the last ones would show the
     * strip's end at once and page through every blog.
     */
    fun lists(query: SearchModel.Query?): ResultLists {
        if (query != shownQuery) {
            shownQuery = query
            shownLists = ResultLists()
        }
        return shownLists
    }

    fun list(query: SearchModel.Query?): LazyListState = if (query == null) browseList else lists(query).page
}

/**
 * Search for posts, blogs and tags, pushed from Discover's search button. The field sits in
 * the bar beside the back button and takes the keyboard at once, as feed apps have it. Before
 * typing it shows the tags and recently updated blogs; after, the tags that match on the
 * device and the blogs and posts the server finds, posts paged as the list scrolls.
 */
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    val page = app.screenState(LocalScreenId.current) { SearchPageState() }
    val model = page.model
    val focus = remember { FocusRequester() }
    val language = LanguageFilter.of(app.prefs.searchLanguage)
    val trimmed = page.query.trim()
    val current = SearchModel.Query(trimmed, language.code)
    LaunchedEffect(Unit) {
        if (page.focused) return@LaunchedEffect
        page.focused = true
        // The keyboard is refused while the push is still running.
        delay(300)
        focus.requestFocus()
    }
    LaunchedEffect(app.server) {
        launch { app.loadTopics() }
        try {
            page.recent = app.client.blogs(null, null, limit = 6).data
        } catch (error: Exception) {
            if (error.isCancellation) throw error
        }
    }
    LaunchedEffect(trimmed, language, app.server, page.attempt) {
        if (trimmed.isEmpty()) {
            model.reset()
            return@LaunchedEffect
        }
        // Waits for a pause in typing; the next keystroke cancels this.
        delay(300)
        model.search(current, app.client)
        if (model.phase == SearchModel.Phase.Unsupported) app.blogIndex.loadIfNeeded(app.client)
    }
    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            SearchField(page.query, { page.query = it }, stringResource(R.string.search_prompt), Modifier.weight(1f), focus)
        }
        val shown = if (trimmed.isEmpty()) null else model.query
        LazyColumn(
            state = page.list(shown),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (trimmed.isEmpty()) {
                browse(app.topics, page.recent)
            } else {
                val topics = app.topics.filter { topic ->
                    topic.name.values.any { it.contains(trimmed, ignoreCase = true) } || topic.slug.contains(trimmed, ignoreCase = true)
                }
                val deviceBlogs = if (model.phase == SearchModel.Phase.Unsupported) app.blogIndex.matches(trimmed) else emptyList()
                results(page, current, language, topics, deviceBlogs) { app.prefs.searchLanguage = it.key }
            }
        }
    }
}

/** A block of the page, as wide as the readable column, with the room above it. */
private fun Modifier.block(top: Dp = 12.dp): Modifier =
    padding(start = 16.dp, end = 16.dp, top = top).widthIn(max = 720.dp).fillMaxWidth()

private fun LazyListScope.browse(topics: List<Topic>, recent: List<Blog>) {
    item(key = "browse-title") { SectionTitle(stringResource(R.string.search_browse_tags), Modifier.block(top = 4.dp)) }
    if (topics.isEmpty()) {
        item(key = "browse-loading") {
            Box(Modifier.block().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    } else {
        topicGrid(topics)
    }
    if (recent.isNotEmpty()) {
        item(key = "recent-title") { SectionTitle(stringResource(R.string.search_recently_updated), Modifier.block(top = 24.dp)) }
        items(recent, key = { "recent-" + it.host }) { BlogRow(it, Modifier.block()) }
    }
}

private fun LazyListScope.results(
    page: SearchPageState,
    current: SearchModel.Query,
    language: LanguageFilter,
    topics: List<Topic>,
    deviceBlogs: List<Blog>,
    onLanguage: (LanguageFilter) -> Unit,
) {
    val model = page.model
    val pending = model.query != current
    when (val phase = model.phase) {
        SearchModel.Phase.Unsupported -> deviceResults(current.text, topics, deviceBlogs)
        is SearchModel.Phase.Failed -> {
            item(key = "summary") { Summary(model, pending, language, onLanguage) }
            tagSection(topics)
            item(key = "failed") {
                val app = LocalApp.current
                LoadFailed(app.message(phase.error), Modifier.block()) { page.attempt++ }
            }
        }
        else -> {
            item(key = "summary") { Summary(model, pending, language, onLanguage) }
            if (!pending && model.isEmpty && topics.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.ManageSearch,
                        stringResource(R.string.search_no_results_title, current.text),
                        stringResource(R.string.search_no_results_description),
                        Modifier.block(),
                    )
                }
                return
            }
            val words = SearchTerms.words(model.query?.text ?: current.text)
            tagSection(topics)
            if (model.blogs.isNotEmpty()) {
                item(key = "blogs-title") { SectionTitle(stringResource(R.string.search_blogs), Modifier.block()) }
                item(key = "blogs") { BlogStrip(model, words, page.lists(model.query).strip) }
            }
            if (model.entries.isNotEmpty() || pending) {
                item(key = "posts-title") { SectionTitle(stringResource(R.string.search_posts), Modifier.block()) }
                posts(model, words)
            }
        }
    }
}

/** The counts for the results on screen and the blog language they cover. */
@Composable
private fun Summary(model: SearchModel, pending: Boolean, language: LanguageFilter, onLanguage: (LanguageFilter) -> Unit) {
    val colors = Explore.colors
    val failed = model.phase is SearchModel.Phase.Failed
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Row(
        modifier = Modifier.block(top = 4.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (model.query != null && !failed) {
            val posts = if (model.entryCountCapped) {
                stringResource(R.string.search_post_count_capped, numbers.format(model.entryCount))
            } else {
                pluralStringResource(R.plurals.search_post_count, model.entryCount, numbers.format(model.entryCount))
            }
            val blogs = pluralStringResource(R.plurals.search_blog_count, model.blogCount, numbers.format(model.blogCount))
            Text("$posts · $blogs", style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pending && !failed) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.weight(1f))
        LanguagePill(language, onLanguage)
    }
}

/** The blog language menu as a small capsule naming the choice, filled while it narrows the results. */
@Composable
private fun LanguagePill(language: LanguageFilter, onChange: (LanguageFilter) -> Unit) {
    val colors = Explore.colors
    var shown by remember { mutableStateOf(false) }
    val label = stringResource(R.string.filter_blog_language)
    val title = languageTitle(language)
    val filtered = language != LanguageFilter.All
    val content = if (filtered) colors.background else colors.label
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (filtered) colors.accent else colors.fill)
                .clickable { shown = true }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = label
                    stateDescription = title
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = content)
            Text(title, style = ExploreType.footnote.copy(fontWeight = FontWeight.Medium), color = content)
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

private fun LazyListScope.tagSection(topics: List<Topic>) {
    if (topics.isEmpty()) return
    item(key = "tags-title") { SectionTitle(stringResource(R.string.search_tags), Modifier.block()) }
    item(key = "tags") {
        FlowRow(Modifier.block(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (topic in topics) TopicChip(topic)
        }
    }
}

/** Blogs in a row that pages sideways, so the posts stay in view. */
@Composable
private fun BlogStrip(model: SearchModel, words: List<String>, state: LazyListState) {
    val height = 112.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    LazyRow(
        state = state,
        modifier = Modifier.padding(top = 12.dp).widthIn(max = 752.dp).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(model.blogs, key = { it.host }) { BlogResultCard(it, words, height) }
        if (model.blogCursor != null) item(key = "more") { BlogStripFooter(model, height) }
    }
}

/** Loads the strip's next blogs once it scrolls into view, or offers a retry. */
@Composable
private fun BlogStripFooter(model: SearchModel, height: Dp) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    Box(Modifier.width(56.dp).height(height), contentAlignment = Alignment.Center) {
        if (model.blogPageError != null) {
            IconButton(onClick = { scope.launch { model.loadMoreBlogs(app.client) } }) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.action_try_again), tint = Explore.colors.label)
            }
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        }
    }
    LaunchedEffect(model.blogs.size) {
        if (model.blogPageError == null) model.loadMoreBlogs(app.client)
    }
}

/** The posts in one card, split by hairlines and paged as the list scrolls; placeholders until the first ones come. */
private fun LazyListScope.posts(model: SearchModel, words: List<String>) {
    val entries = model.entries
    if (entries.isEmpty()) {
        item(key = "posts-placeholder") {
            PostsCard(top = true, bottom = true) {
                repeat(3) { index ->
                    EntryPlaceholder()
                    if (index < 2) Hairline()
                }
            }
        }
        return
    }
    val hasMore = model.entryCursor != null
    itemsIndexed(entries, key = { _, entry -> "post-" + entry.id }) { index, entry ->
        PostsCard(top = index == 0, bottom = index == entries.lastIndex && !hasMore) {
            EntryRow(entry, highlight = words)
            if (index < entries.lastIndex) Hairline()
        }
    }
    if (hasMore) {
        item(key = "posts-footer") {
            val app = LocalApp.current
            PostsCard(top = false, bottom = true) {
                PageFooter(
                    isLoading = model.isLoadingMoreEntries,
                    error = model.entryPageError?.let { app.message(it) },
                    hasMore = true,
                    itemCount = entries.size,
                    endText = null,
                ) { model.loadMoreEntries(app.client) }
            }
        }
    }
}

/** A slice of the posts' card: rounded at the ends of the run, square where the slices meet. */
@Composable
private fun PostsCard(top: Boolean, bottom: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val corner = 22.dp
    val upper = if (top) corner else 0.dp
    val lower = if (bottom) corner else 0.dp
    Column(
        modifier = Modifier
            .block(top = if (top) 12.dp else 0.dp)
            .clip(RoundedCornerShape(upper, upper, lower, lower))
            .background(Explore.colors.card)
            .padding(horizontal = 14.dp),
        content = content,
    )
}

/** A server from before search: tags, and blogs matched on the device. */
private fun LazyListScope.deviceResults(query: String, topics: List<Topic>, blogs: List<Blog>) {
    item(key = "unsupported") {
        Text(
            stringResource(R.string.search_posts_unsupported),
            style = ExploreType.subheadline,
            color = Explore.colors.secondaryLabel,
            modifier = Modifier.block(top = 8.dp),
        )
    }
    if (topics.isEmpty() && blogs.isEmpty()) {
        item(key = "device-empty") { Box(Modifier.block()) { BlogSearchEmpty(query) } }
        return
    }
    tagSection(topics)
    if (blogs.isNotEmpty()) {
        item(key = "device-title") { SectionTitle(stringResource(R.string.search_blogs), Modifier.block()) }
        items(blogs, key = { "device-" + it.host }) { BlogRow(it, Modifier.block()) }
    }
}

private fun LazyListScope.topicGrid(topics: List<Topic>) {
    items(topics.chunked(2), key = { row -> "tiles-" + row.joinToString { it.slug } }) { row ->
        Row(Modifier.block(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (topic in row) TopicTile(topic, Modifier.weight(1f))
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** A tile for a topic, with its icon large and faint in the corner. */
@Composable
fun TopicTile(topic: Topic, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = Explore.colors
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .clickable { app.navigate(Route.TopicPage(topic.slug)) },
    ) {
        Icon(
            TopicStyle.icon(topic.slug), null,
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp)
                .size(64.dp)
                .rotate(-12f),
            tint = colors.quaternaryLabel,
        )
        Text(topic.name(AppLanguage.current), style = ExploreType.headline, color = colors.label, modifier = Modifier.padding(14.dp))
    }
}

/** A tag that matched a search, as a small capsule leading to its posts. */
@Composable
fun TopicChip(topic: Topic) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.card)
            .clickable { app.navigate(Route.TopicPage(topic.slug)) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(TopicStyle.icon(topic.slug), null, Modifier.size(16.dp), tint = colors.label)
        Text(topic.name(AppLanguage.current), style = ExploreType.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.label)
    }
}

@Composable
fun BlogRow(blog: Blog, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .clickable { app.navigate(Route.BlogPage(blog.ref)) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlogAvatar(blog.host, blog.name, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(blog.name, style = ExploreType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                blog.about.ifEmpty { Formatting.displayHost(blog.host) },
                style = ExploreType.subheadline,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.tertiaryLabel)
    }
}

/** A blog a search found, sized to sit in a row of them. A long press shows its actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlogResultCard(blog: Blog, words: List<String>, height: Dp) {
    val app = LocalApp.current
    val colors = Explore.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .width(248.dp)
                .height(height)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .combinedClickable(onLongClick = { menu = true }) { app.navigate(Route.BlogPage(blog.ref)) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BlogAvatar(blog.host, blog.name, 36.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        SearchTerms.marked(blog.name, words, colors.mark),
                        style = ExploreType.headline,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Formatting.displayHost(blog.host), style = ExploreType.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (blog.about.isNotEmpty()) {
                Text(
                    SearchTerms.marked(SearchTerms.snippet(blog.about, words), words, colors.mark),
                    style = ExploreType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            BlogActions(blog.ref, blog.feedUrl) { menu = false }
        }
    }
}

/** A tag page's posts and how far they were scrolled. */
class TopicPageState(slug: String) {
    val feed = FeedModel(FeedModel.Source.Latest, tag = slug)
    val list = LazyListState()
}

/** The latest posts Explore filed under one tag. */
@Composable
fun TopicScreen(slug: String) {
    val app = LocalApp.current
    val colors = Explore.colors
    val page = app.screenState(LocalScreenId.current) { TopicPageState(slug) }
    val language = LanguageFilter.of(app.prefs.topicLanguage)
    val name = app.topicName(slug) ?: slug
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(language, app.server) {
        page.feed.language = language
        page.feed.refreshIfNeeded(app.client)
    }
    PushedScreen(
        title = name,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Language, stringResource(R.string.filter_blog_language)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (option in LanguageFilter.entries) {
                        DropdownMenuItem(
                            text = { Text(languageTitle(option)) },
                            leadingIcon = { RadioButton(selected = option == language, onClick = null) },
                            onClick = {
                                app.prefs.topicLanguage = option.key
                                menu = false
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        EntryList(
            feed = page.feed,
            modifier = Modifier.padding(padding),
            state = page.list,
            onRefresh = { page.feed.load(app.client, fresh = true) },
            header = {
                item(key = "banner") {
                    Box(
                        Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(colors.secondaryBackground)
                            .semantics(mergeDescendants = true) { heading() },
                    ) {
                        Icon(
                            TopicStyle.icon(slug), null,
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 24.dp, y = (-10).dp)
                                .size(150.dp)
                                .rotate(-12f),
                            tint = colors.quaternaryLabel,
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = ExploreType.largeTitle, color = colors.label)
                            Text(stringResource(R.string.topic_banner_description), style = ExploreType.subheadline, color = colors.secondaryLabel)
                        }
                    }
                }
            },
        ) {
            EmptyState(TopicStyle.icon(slug), stringResource(R.string.discover_no_posts_yet), stringResource(R.string.topic_no_posts))
        }
    }
}
