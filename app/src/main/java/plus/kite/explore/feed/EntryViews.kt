package plus.kite.explore.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import plus.kite.explore.R
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.ReportTarget
import plus.kite.explore.app.Route
import plus.kite.explore.app.ShareSubject
import plus.kite.explore.app.SignInMode
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.LinkStatus
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.EntryPlaceholder
import plus.kite.explore.design.EntryThumbnail
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.PageFooter
import plus.kite.explore.design.TopicStyle

/**
 * A stream of posts in one run, newest first, as flat rows split by hairlines; each row says
 * when its post came out. Notices the feed has go between the posts. It brings placeholders,
 * errors, an empty state and paging; the header scrolls with the posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryList(
    feed: FeedModel,
    modifier: Modifier = Modifier,
    showsBlog: Boolean = true,
    contextBlog: BlogRef? = null,
    endText: String? = stringResource(R.string.list_end_caught_up),
    topRequest: Int = 0,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp),
    onRefresh: suspend () -> Unit,
    header: LazyListScope.() -> Unit = {},
    empty: @Composable () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(topRequest) {
        if (topRequest > 0) state.animateScrollToItem(0)
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                onRefresh()
                refreshing = false
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Explore.colors.background),
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()
            val phase = feed.phase
            when {
                feed.showsPlaceholders -> items(4) { index ->
                    Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) {
                        EntryPlaceholder()
                        if (index < 3) Hairline()
                    }
                }
                phase is FeedModel.Phase.Failed -> item {
                    LoadFailed(app.message(phase.error)) { feed.load(app.client, fresh = true) }
                }
                feed.entries.isEmpty() -> item { empty() }
                else -> {
                    val items = StreamItem.items(feed.entries, feed.notices)
                    val blog = contextBlog ?: feed.blog?.ref
                    items(items, key = { it.key }) { item ->
                        Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) {
                            when (item) {
                                is StreamItem.Post -> EntryRow(item.entry, showsBlog = showsBlog, contextBlog = blog)
                                is StreamItem.Note -> NoticeRow(item.notice)
                            }
                            if (item.key != items.last().key) Hairline()
                        }
                    }
                    item(key = "footer") {
                        Box(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) {
                            PageFooter(
                                isLoading = feed.isLoadingMore,
                                error = feed.loadMoreError?.let { app.message(it) },
                                hasMore = feed.hasMore,
                                itemCount = feed.entries.size,
                                endText = endText,
                            ) { feed.loadMore(app.client) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One post, laid out as on the website: its blog and time first, then the title and two lines
 * of excerpt beside the thumbnail, then its tags. The title, excerpt and thumbnail open the
 * post on the author's site; the blog's name leads to its page. Titles and excerpts keep the
 * blog's own language and are never translated.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(entry: Entry, showsBlog: Boolean = true, contextBlog: BlogRef? = null) {
    val app = LocalApp.current
    val colors = Explore.colors
    val blog = entry.blog ?: contextBlog
    val read = app.isRead(entry)
    var menuShown by remember { mutableStateOf(false) }
    val readLabel = stringResource(R.string.entry_read_state)
    val open = { app.openPost(entry, blog) }
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Byline(entry, blog, showsBlog, showMenu = entry.imagePath == null) { menuShown = true }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = open,
                            onLongClick = { menuShown = true },
                        )
                        .semantics { if (read) stateDescription = readLabel },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        entry.title,
                        style = ExploreType.headline,
                        color = if (read) colors.secondaryLabel else colors.label,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val excerpt = entry.excerpt
                    if (!excerpt.isNullOrEmpty()) {
                        Text(
                            excerpt,
                            style = ExploreType.subheadline,
                            color = if (read) colors.tertiaryLabel else colors.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val image = entry.imagePath
            if (image != null) {
                // The menu takes the room under the thumbnail, so the byline keeps the blog's name whole.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryThumbnail(
                        app.client.imageUrl(image), 96.dp, 64.dp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(if (read) 0.6f else 1f)
                            .clickable(onClick = open),
                    )
                    MoreButton { menuShown = true }
                }
            }
        }
        val tags = entry.tags.take(3).mapNotNull { slug -> app.topicName(slug)?.let { slug to it } }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((slug, name) in tags) {
                    Row(
                        modifier = Modifier.clickable { app.navigate(Route.TopicPage(slug)) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(TopicStyle.icon(slug), null, Modifier.size(13.dp), tint = colors.secondaryLabel)
                        Text(name, style = ExploreType.caption, color = colors.secondaryLabel, maxLines = 1)
                    }
                }
            }
        }
        Box {
            DropdownMenu(expanded = menuShown, onDismissRequest = { menuShown = false }) {
                EntryActions(entry, blog) { menuShown = false }
            }
        }
    }
}

@Composable
private fun Byline(entry: Entry, blog: BlogRef?, showsBlog: Boolean, showMenu: Boolean, onMenu: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    val time = entry.publishedAt?.let { Formatting.relative(context, it) } ?: stringResource(R.string.entry_date_unknown)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Beside a thumbnail the byline is narrow: the badge keeps to its icon so the blog's name fits.
    val compact = maxWidth < 330.dp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showsBlog && blog != null) {
            val label = stringResource(R.string.entry_blog_label, blog.name)
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { app.navigate(Route.BlogPage(blog)) }
                    .semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BlogAvatar(blog.host, blog.name, 18.dp)
                Text(
                    blog.name,
                    style = ExploreType.footnote.copy(fontWeight = FontWeight.Medium),
                    color = colors.label.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("·", style = ExploreType.footnote, color = colors.secondaryLabel)
        }
        Text(time, style = ExploreType.footnote, color = colors.secondaryLabel, maxLines = 1)
        Text("·", style = ExploreType.footnote, color = colors.secondaryLabel)
        LinkStatusBadge(entry, compact)
        }
        if (showMenu) MoreButton(onMenu)
    }
    }
}

@Composable
private fun MoreButton(onClick: () -> Unit) {
    val label = stringResource(R.string.action_more)
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 22.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(18.dp), tint = Explore.colors.secondaryLabel)
    }
}

/** Actions for a post, shared by its menu and its long press. */
@Composable
fun EntryActions(entry: Entry, blog: BlogRef?, dismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    fun act(action: () -> Unit) {
        dismiss()
        action()
    }
    MenuRow(stringResource(R.string.entry_read_on_site), Icons.AutoMirrored.Rounded.OpenInNew) { act { app.openPost(entry, blog) } }
    MenuRow(stringResource(R.string.entry_open_in_browser), Icons.Rounded.Public) {
        act {
            app.markRead(entry.url)
            app.openInBrowser(entry.url, app.sourceTag)
        }
    }
    MenuRow(stringResource(R.string.action_share), Icons.Rounded.Share) { act { app.sheet = AppSheet.Share(ShareSubject.Post(entry, blog)) } }
    MenuRow(stringResource(R.string.action_copy_link), Icons.Rounded.Link) {
        act {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(entry.title, entry.url))
            // Android 13 and later confirm a copy themselves.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) app.showToast(app.string(R.string.toast_link_copied), Icons.Rounded.Link)
        }
    }
    if (blog != null) {
        HorizontalDivider()
        MenuRow(stringResource(R.string.entry_go_to_blog), Icons.Rounded.GridView) { act { app.navigate(Route.BlogPage(blog)) } }
        val following = app.isFollowing(blog.host)
        MenuRow(
            stringResource(if (following) R.string.action_unfollow else R.string.action_follow),
            if (following) Icons.Rounded.HeartBroken else Icons.Rounded.FavoriteBorder,
        ) { act { app.toggleFollow(blog) } }
    }
    val unknown = app.linkStatus(entry).first == LinkStatus.Unknown
    if (unknown || blog != null) HorizontalDivider()
    if (unknown) {
        MenuRow(stringResource(R.string.entry_check_link), Icons.Rounded.VerifiedUser) { act { app.checkLink(entry) } }
    }
    if (blog != null) {
        MenuRow(stringResource(R.string.action_report), Icons.Rounded.Flag, destructive = true) {
            act {
                val target = ReportTarget(blog.host, blog.name, entry.id, entry.title)
                app.sheet = if (app.isSignedIn) AppSheet.Report(target) else AppSheet.SignIn(SignInMode.SignIn)
            }
        }
    }
}

@Composable
fun MenuRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) Explore.colors.red else Explore.colors.label
    DropdownMenuItem(
        text = { Text(text, color = color, style = ExploreType.body) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick,
    )
}
