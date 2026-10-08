package plus.kite.explore.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.kite.explore.R
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.AppTab
import plus.kite.explore.app.DiscoverStream
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Route
import plus.kite.explore.app.SignInMode
import plus.kite.explore.data.AppLanguage
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.TopicStyle
import plus.kite.explore.feed.EntryList
import plus.kite.explore.feed.FeedModel

/** Discover's streams and their scroll positions, kept while other tabs show. */
class DiscoverState {
    val latest = FeedModel(FeedModel.Source.Latest)
    val recommended = FeedModel(FeedModel.Source.Recommended)
    val following = FeedModel(FeedModel.Source.Following)
    val latestList = LazyListState()
    val recommendedList = LazyListState()
    val followingList = LazyListState()
    var tag by mutableStateOf<String?>(null)
    val topRequests = mutableStateMapOf<DiscoverStream, Int>()
}

/**
 * Explore's streams in one place, as on the website: the latest posts, the recommended ones and
 * the reader's follows, as tabs over the same filters. The streams sit side by side, so a swipe
 * moves between them as a tap on a tab does, and each keeps its place. The bar holds only the
 * tabs, with the filter and search at either end, so the posts start high on the screen.
 */
@Composable
fun DiscoverScreen() {
    val app = LocalApp.current
    val state = app.discover
    val language = LanguageFilter.of(app.prefs.discoverLanguage)
    val filtered = language != LanguageFilter.All || state.tag != null
    val pager = rememberPagerState(initialPage = app.stream.ordinal) { DiscoverStream.entries.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val stream = DiscoverStream.entries[page]
            if (stream != app.stream) app.stream = stream
        }
    }
    LaunchedEffect(app.stream) {
        if (pager.currentPage != app.stream.ordinal) pager.animateScrollToPage(app.stream.ordinal)
    }
    LaunchedEffect(app.discoverRetaps) {
        if (app.discoverRetaps > 0) state.topRequests[app.stream] = (state.topRequests[app.stream] ?: 0) + 1
    }

    Column(Modifier.fillMaxSize().background(Explore.colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterButton(filtered) { app.sheet = AppSheet.Filter }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                StreamTabs(position = { pager.currentPage + pager.currentPageOffsetFraction }) { stream ->
                    app.stream = stream
                    scope.launch { pager.animateScrollToPage(stream.ordinal) }
                }
            }
            IconButton(onClick = { app.navigate(Route.Search) }) {
                Icon(Icons.Rounded.Search, stringResource(R.string.action_search), tint = Explore.colors.label)
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 2) { page ->
            when (DiscoverStream.entries[page]) {
                DiscoverStream.Latest -> LatestStream(language, filtered)
                DiscoverStream.Recommended -> RecommendedStream(language, filtered)
                DiscoverStream.Following -> FollowingStream(state.following, state.followingList, language, state.tag, state.topRequests[DiscoverStream.Following] ?: 0)
            }
        }
    }
}

@Composable
private fun LatestStream(language: LanguageFilter, filtered: Boolean) {
    val app = LocalApp.current
    val state = app.discover
    val feed = state.latest
    LaunchedEffect(language, state.tag, app.server) {
        feed.language = language
        feed.tag = state.tag
        feed.refreshIfNeeded(app.client)
    }
    EntryList(
        feed = feed,
        state = state.latestList,
        topRequest = state.topRequests[DiscoverStream.Latest] ?: 0,
        onRefresh = { feed.load(app.client, fresh = true) },
        header = {
            if (app.notice.isNotEmpty() && app.prefs.dismissedNotice != app.notice) {
                item(key = "site-notice") { NoticeBanner(app.notice) }
            }
        },
    ) {
        EmptyState(
            Icons.Rounded.Explore,
            stringResource(if (filtered) R.string.discover_no_matching_posts else R.string.discover_no_posts_yet),
            stringResource(if (filtered) R.string.discover_no_posts_match else R.string.discover_posts_show_up),
        )
    }
}

@Composable
private fun RecommendedStream(language: LanguageFilter, filtered: Boolean) {
    val app = LocalApp.current
    val state = app.discover
    val feed = state.recommended
    LaunchedEffect(language, state.tag, app.server) {
        feed.language = language
        feed.tag = state.tag
        feed.refreshIfNeeded(app.client)
    }
    EntryList(
        feed = feed,
        state = state.recommendedList,
        endText = stringResource(R.string.list_end_recommended),
        topRequest = state.topRequests[DiscoverStream.Recommended] ?: 0,
        onRefresh = { feed.load(app.client, fresh = true) },
    ) {
        if (filtered) {
            EmptyState(Icons.Rounded.AutoAwesome, stringResource(R.string.recommended_none_title), stringResource(R.string.discover_no_posts_match))
        } else {
            // Nothing rated yet: the server has no model to rate posts.
            EmptyState(Icons.Rounded.AutoAwesome, stringResource(R.string.recommended_not_yet_title), stringResource(R.string.recommended_not_yet_description)) {
                PrimaryButton(onClick = { app.stream = DiscoverStream.Latest }) { Text(stringResource(R.string.recommended_see_latest)) }
            }
        }
    }
}

/**
 * New posts from the blogs the reader follows, Discover's third stream. Following needs an
 * account; everything else works without one, so before signing in the stream invites the reader to.
 */
@Composable
private fun FollowingStream(feed: FeedModel, list: LazyListState, language: LanguageFilter, tag: String?, topRequest: Int) {
    val app = LocalApp.current
    when {
        app.isSignedIn -> {
            LaunchedEffect(language, tag, app.generation, app.server) {
                feed.language = language
                feed.tag = tag
                feed.version = app.generation
                feed.refreshIfNeeded(app.client)
                feed.lastError?.let { app.handleAuthError(it) }
            }
            EntryList(
                feed = feed,
                state = list,
                endText = stringResource(R.string.list_end_following),
                topRequest = topRequest,
                onRefresh = {
                    app.loadFollows()
                    feed.load(app.client, fresh = true)
                },
                header = {
                    if (app.followedHosts.isNotEmpty()) {
                        item(key = "following-header") { FollowingHeader(app.followedHosts.size) }
                    }
                },
            ) {
                if (app.followedHosts.isEmpty()) {
                    EmptyState(Icons.Rounded.FavoriteBorder, stringResource(R.string.following_none_title), stringResource(R.string.following_none_description)) {
                        PrimaryButton(onClick = { app.tab = AppTab.Blogs }) { Text(stringResource(R.string.following_browse_blogs)) }
                    }
                } else {
                    EmptyState(Icons.Rounded.Inbox, stringResource(R.string.following_no_posts_title), stringResource(R.string.following_no_posts_description))
                }
            }
        }
        app.isRestoringSession -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> FollowingWelcome()
    }
}

/** How many blogs the reader follows, with the way to manage them. */
@Composable
private fun FollowingHeader(count: Int) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.following_header, count), style = ExploreType.subheadline, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
        TextButton(onClick = { app.navigate(Route.Subscriptions) }) {
            Text(stringResource(R.string.following_manage), style = ExploreType.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.accent)
        }
    }
}

/** The Following stream before signing in, kept plain: grays and a black or white button. */
@Composable
private fun FollowingWelcome() {
    val app = LocalApp.current
    val colors = Explore.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(44.dp), tint = colors.secondaryLabel)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.following_welcome_title), style = ExploreType.title2, color = colors.label, textAlign = TextAlign.Center)
            Text(stringResource(R.string.following_welcome_description), style = ExploreType.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        }
        Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(onClick = { app.sheet = AppSheet.SignIn(SignInMode.SignIn) }, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.account_sign_in), style = ExploreType.headline)
            }
            if (app.registrationEnabled) {
                TextButton(onClick = { app.sheet = AppSheet.SignIn(SignInMode.Register) }) {
                    Text(stringResource(R.string.account_create), style = ExploreType.headline, color = colors.secondaryLabel)
                }
            }
        }
    }
}

/** The site notice maintainers set in the admin console. */
@Composable
private fun NoticeBanner(text: String) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.secondaryBackground)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Campaign, null, Modifier.size(20.dp), tint = colors.secondaryLabel)
        Text(text, style = ExploreType.subheadline, color = colors.label, modifier = Modifier.weight(1f))
        IconButton(onClick = { app.prefs.dismissedNotice = text }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.action_dismiss), Modifier.size(16.dp), tint = colors.secondaryLabel)
        }
    }
}

/**
 * Discover's streams as tabs in the middle of the bar, as feed apps carry them. The current tab
 * is underlined, and the line follows the streams as they slide; it is drawn from the pager's
 * position each frame without composing the tabs again.
 */
@Composable
private fun StreamTabs(position: () -> Float, select: (DiscoverStream) -> Unit) {
    val colors = Explore.colors
    val bounds = remember { mutableStateMapOf<DiscoverStream, Pair<Float, Float>>() }
    val last = DiscoverStream.entries.size - 1
    val nearest by remember { derivedStateOf { DiscoverStream.entries[kotlin.math.round(position()).toInt().coerceIn(0, last)] } }
    val label = stringResource(R.string.discover_feeds)
    val line = colors.label
    Row(
        modifier = Modifier
            .semantics { contentDescription = label }
            .drawBehind {
                val clamped = position().coerceIn(0f, last.toFloat())
                val lower = clamped.toInt()
                val from = bounds[DiscoverStream.entries[lower]] ?: return@drawBehind
                val to = bounds[DiscoverStream.entries[minOf(lower + 1, last)]] ?: return@drawBehind
                val progress = clamped - lower
                val x = from.first + (to.first - from.first) * progress
                val width = from.second + (to.second - from.second) * progress
                val height = 2.5.dp.toPx()
                drawRoundRect(line, Offset(x, size.height - height), Size(width, height), CornerRadius(height / 2))
            },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (stream in DiscoverStream.entries) {
            val highlighted = stream == nearest
            Text(
                stringResource(
                    when (stream) {
                        DiscoverStream.Latest -> R.string.stream_latest
                        DiscoverStream.Recommended -> R.string.stream_recommended
                        DiscoverStream.Following -> R.string.stream_following
                    },
                ),
                style = ExploreType.body.copy(fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal),
                color = if (highlighted) colors.label else colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { select(stream) }
                    .padding(vertical = 8.dp)
                    .onGloballyPositioned { bounds[stream] = it.positionInParent().x to it.size.width.toFloat() }
                    .semantics { selected = stream == nearest },
            )
        }
    }
}

/** Opens the filter sheet. While a blog language or a tag narrows the streams, the button is filled. */
@Composable
private fun FilterButton(filtered: Boolean, onClick: () -> Unit) {
    val colors = Explore.colors
    val label = stringResource(R.string.action_filter)
    val inUse = stringResource(R.string.filter_in_use)
    val semantics = Modifier.semantics { if (filtered) stateDescription = inUse }
    if (filtered) {
        FilledIconButton(
            onClick = onClick,
            modifier = semantics,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent, contentColor = colors.background),
        ) { Icon(Icons.Rounded.FilterList, label) }
    } else {
        IconButton(onClick = onClick, modifier = semantics) { Icon(Icons.Rounded.FilterList, label, tint = colors.label) }
    }
}

/**
 * What narrows Discover's streams, as the website's filter panel holds it: the blog language
 * as a switch and the tags as tiles with their icons. Picking a tag closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val state = app.discover
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val language = LanguageFilter.of(app.prefs.discoverLanguage)
    LaunchedEffect(Unit) { app.loadTopics() }
    fun choose(slug: String?) {
        state.tag = slug
        // The tile shows the choice before the sheet goes.
        scope.launch {
            delay(180)
            sheet.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.action_filter), style = ExploreType.headline, color = colors.label, modifier = Modifier.weight(1f))
                if (language != LanguageFilter.All || state.tag != null) {
                    TextButton(onClick = {
                        app.prefs.discoverLanguage = LanguageFilter.All.key
                        state.tag = null
                    }) { Text(stringResource(R.string.filter_reset)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterSection(stringResource(R.string.filter_blog_language))
                LanguageSwitch(language) { app.prefs.discoverLanguage = it.key }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterSection(stringResource(R.string.filter_tags))
                if (app.topics.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val tiles = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_all)) +
                        app.topics.map { it.slug to it.name(AppLanguage.current) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in tiles.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((slug, name) in row) {
                                    TagTile(name, slug, selected = state.tag == slug, Modifier.weight(1f)) { choose(slug) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String) {
    Text(title, style = ExploreType.footnote.copy(fontWeight = FontWeight.SemiBold), color = Explore.colors.secondaryLabel)
}

/** All, Chinese and English blogs, as a segmented switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSwitch(language: LanguageFilter, onChange: (LanguageFilter) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        LanguageFilter.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == language,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, LanguageFilter.entries.size),
                icon = {},
            ) { Text(languageTitle(option)) }
        }
    }
}

@Composable
fun languageTitle(option: LanguageFilter): String = stringResource(
    when (option) {
        LanguageFilter.All -> R.string.filter_all
        LanguageFilter.Zh -> R.string.filter_chinese
        LanguageFilter.En -> R.string.filter_english
    },
)

/** A tag's icon and name; the chosen one takes the brand color, as on the website. */
@Composable
private fun TagTile(name: String, slug: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Explore.colors
    val tint = if (selected) colors.accent else colors.label
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (slug == null) Icons.Rounded.GridView else TopicStyle.icon(slug), null, Modifier.size(18.dp), tint = tint.copy(alpha = if (selected) 1f else 0.7f))
        Text(
            name,
            style = ExploreType.subheadline.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
