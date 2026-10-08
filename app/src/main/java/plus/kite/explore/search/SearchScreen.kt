package plus.kite.explore.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import plus.kite.explore.R
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.LocalScreenId
import plus.kite.explore.app.Route
import plus.kite.explore.blogs.BlogSearchEmpty
import plus.kite.explore.blogs.SearchField
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.AppLanguage
import plus.kite.explore.data.Blog
import plus.kite.explore.data.Topic
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.SectionTitle
import plus.kite.explore.design.TopicStyle
import plus.kite.explore.discover.languageTitle
import plus.kite.explore.feed.EntryList
import plus.kite.explore.feed.FeedModel

/**
 * Search for blogs and tags, pushed from Discover's search button. The field sits in the bar
 * beside the back button and takes the keyboard at once, as feed apps have it. Before typing
 * it shows the tags and recently updated blogs; after, the matching tags and blogs.
 */
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val trimmed = query.trim()
    LaunchedEffect(Unit) {
        // The keyboard is refused while the push is still running.
        delay(300)
        focus.requestFocus()
    }
    LaunchedEffect(app.server) {
        app.blogIndex.loadIfNeeded(app.client)
        app.loadTopics()
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
            SearchField(query, { query = it }, stringResource(R.string.search_prompt), Modifier.weight(1f), focus)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (trimmed.isEmpty()) browse(app.topics, app.blogIndex.blogs) else results(trimmed, app.topics, app.blogIndex.matches(trimmed))
        }
    }
}

private fun LazyListScope.browse(topics: List<Topic>, blogs: List<Blog>) {
    item { Constrained { SectionTitle(stringResource(R.string.search_browse_tags)) } }
    if (topics.isEmpty()) {
        item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    } else {
        topicGrid(topics)
    }
    if (blogs.isNotEmpty()) {
        item { Constrained { SectionTitle(stringResource(R.string.search_recently_updated), Modifier.padding(top = 12.dp)) } }
        items(blogs.take(6), key = { "recent-" + it.host }) { BlogRow(it) }
    }
}

private fun LazyListScope.results(query: String, topics: List<Topic>, blogs: List<Blog>) {
    val matchingTopics = topics.filter { topic ->
        topic.name.values.any { it.contains(query, ignoreCase = true) } || topic.slug.contains(query, ignoreCase = true)
    }
    if (matchingTopics.isEmpty() && blogs.isEmpty()) {
        item { BlogSearchEmpty(query) }
        return
    }
    if (matchingTopics.isNotEmpty()) {
        item { Constrained { SectionTitle(stringResource(R.string.search_tags)) } }
        topicGrid(matchingTopics)
    }
    if (blogs.isNotEmpty()) {
        item { Constrained { SectionTitle(stringResource(R.string.search_blogs), Modifier.padding(top = if (matchingTopics.isEmpty()) 0.dp else 12.dp)) } }
        items(blogs, key = { "match-" + it.host }) { BlogRow(it) }
    }
}

private fun LazyListScope.topicGrid(topics: List<Topic>) {
    items(topics.chunked(2), key = { row -> "tiles-" + row.joinToString { it.slug } }) { row ->
        Constrained {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (topic in row) TopicTile(topic, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Constrained(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { content() }
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

@Composable
fun BlogRow(blog: Blog) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = Modifier
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
