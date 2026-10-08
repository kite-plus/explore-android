package plus.kite.explore.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import plus.kite.explore.R
import plus.kite.explore.app.AppModel
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.AppTab
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Route
import plus.kite.explore.core.Formatting
import plus.kite.explore.core.Opml
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.Blog
import plus.kite.explore.data.OpmlImport
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.FormError
import plus.kite.explore.design.FormField
import plus.kite.explore.design.FormSection
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.MarkTile
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.RowDivider
import plus.kite.explore.design.SecondaryButton
import plus.kite.explore.design.SettingsRow

/** A file in the cache that other apps may read while it is shared. */
fun sharedFile(context: Context, name: String, bytes: ByteArray): Uri {
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, name)
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

fun shareFile(context: Context, uri: Uri, type: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

/** Up to limit bytes of a stream, so an oversized file is noticed without reading it all. */
private fun readAtMost(stream: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** The blogs the reader follows, with unfollow, OPML import and OPML export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var blogs by remember { mutableStateOf<List<Blog>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<OpmlImport?>(null) }
    var menu by remember { mutableStateOf(false) }

    suspend fun load() {
        if (!app.isSignedIn) {
            blogs = emptyList()
            loaded = true
            return
        }
        try {
            val list = app.client.subscriptions()
            blogs = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            app.replaceFollowedHosts(list.map { it.host }.toSet())
            failure = null
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            app.handleAuthError(error)
            failure = error
        } finally {
            loaded = true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { readAtMost(it, Opml.MAX_BYTES + 1) }
                } catch (_: Exception) {
                    null
                }
            }
            when {
                data == null -> app.showError(plus.kite.explore.data.ApiError.Transport("unreadable file"))
                data.size > Opml.MAX_BYTES -> app.showToast(app.string(R.string.opml_too_large), Icons.Rounded.Upload, isError = true)
                else -> {
                    importing = true
                    try {
                        imported = app.client.importSubscriptions(data)
                        app.showToast(app.string(R.string.opml_import_finished), Icons.Rounded.Download)
                        load()
                    } catch (error: Exception) {
                        app.handleAuthError(error)
                        app.showError(error)
                    } finally {
                        importing = false
                    }
                }
            }
        }
    }
    fun choose() = picker.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "text/plain", "application/octet-stream"))

    LaunchedEffect(app.generation, app.server) { load() }

    PushedScreen(
        title = stringResource(R.string.stream_following),
        background = colors.groupedBackground,
        actions = {
            if (app.isSignedIn) {
                if (importing) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                } else {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "OPML") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.opml_import)) },
                                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                onClick = {
                                    menu = false
                                    choose()
                                },
                            )
                            if (blogs.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.opml_export)) },
                                    leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                                    onClick = {
                                        menu = false
                                        val document = Opml.document(app.string(R.string.opml_document_title), blogs)
                                        shareFile(context, sharedFile(context, "Explore.opml", document.encodeToByteArray()), "text/x-opml")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            imported?.let { result -> item { ImportSummary(result) } }
            val error = failure
            when {
                !loaded -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error != null && blogs.isEmpty() -> item { LoadFailed(app.message(error)) { load() } }
                blogs.isEmpty() -> item {
                    EmptyState(Icons.Rounded.FavoriteBorder, stringResource(R.string.following_none_title), stringResource(R.string.following_none_description)) {
                        PrimaryButton(onClick = { app.tab = AppTab.Blogs }) { Text(stringResource(R.string.following_browse_blogs)) }
                        if (app.isSignedIn) {
                            TextButton(onClick = ::choose, enabled = !importing) { Text(stringResource(R.string.opml_import)) }
                        }
                    }
                }
                else -> item {
                    FormSection(footer = stringResource(R.string.opml_footer)) {
                        blogs.forEachIndexed { index, blog ->
                            key(blog.host) {
                                SwipeToUnfollow(blog) {
                                    blogs = blogs.filter { it.host != blog.host }
                                    app.toggleFollow(blog.ref)
                                }
                            }
                            if (index < blogs.lastIndex) Hairline(Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToUnfollow(blog: Blog, unfollow: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) unfollow()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Drawn only while swiping, so it never shows at the card's rounded corners.
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Row(
                Modifier.fillMaxSize().background(colors.red).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.HeartBroken, null, tint = androidx.compose.ui.graphics.Color.White)
                Text(" " + stringResource(R.string.action_unfollow), color = androidx.compose.ui.graphics.Color.White, style = ExploreType.subheadline)
            }
        },
    ) {
        BlogLine(blog, Modifier.background(colors.card)) { app.navigate(Route.BlogPage(blog.ref)) }
    }
}

/** A blog's avatar, name and host in a list. */
@Composable
fun BlogLine(blog: Blog, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = Explore.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlogAvatar(blog.host, blog.name, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(blog.name, style = ExploreType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Formatting.displayHost(blog.host), style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** What an import did, and the blogs Explore does not list yet with a way to submit them. */
@Composable
private fun ImportSummary(result: OpmlImport) {
    val app = LocalApp.current
    val colors = Explore.colors
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FormSection(stringResource(R.string.opml_import_header)) {
            CountRow(stringResource(R.string.opml_read_from_file), result.outlines)
            RowDivider(inset = false)
            CountRow(stringResource(R.string.opml_newly_followed), result.added)
            RowDivider(inset = false)
            CountRow(stringResource(R.string.opml_already_followed), result.alreadyFollowing)
            if (result.ignored > 0) {
                RowDivider(inset = false)
                CountRow(stringResource(R.string.opml_past_limit), result.ignored)
            }
        }
        if (result.notListed.isNotEmpty()) {
            FormSection(stringResource(R.string.opml_not_listed), stringResource(R.string.opml_not_listed_footer)) {
                result.notListed.forEachIndexed { index, outline ->
                    val site = siteAddress(outline)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(outline.title.ifEmpty { site }, style = ExploreType.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val host = Formatting.hostOf(site)
                            if (host != null && outline.title.isNotEmpty()) {
                                Text(Formatting.displayHost(host), style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 1)
                            }
                        }
                        SecondaryButton(onClick = { app.sheet = AppSheet.Submit(site, outline.feedUrl) }) { Text(stringResource(R.string.action_submit)) }
                    }
                    if (index < result.notListed.lastIndex) Hairline(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun CountRow(title: String, count: Int) {
    val colors = Explore.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ExploreType.body, color = colors.label, modifier = Modifier.weight(1f))
        Text("%,d".format(count), style = ExploreType.body, color = colors.secondaryLabel)
    }
}

/** The blog's address for the submit form; a feed with no site address stands for the site at its origin. */
fun siteAddress(outline: OpmlImport.Outline): String {
    if (outline.siteUrl.isNotEmpty()) return outline.siteUrl
    return try {
        val feed = java.net.URI(outline.feedUrl)
        val port = if (feed.port >= 0) ":${feed.port}" else ""
        if (feed.scheme != null && feed.host != null) "${feed.scheme}://${feed.host}$port/" else outline.feedUrl
    } catch (_: Exception) {
        outline.feedUrl
    }
}

/** Blogs the reader has proved they own. */
@Composable
fun OwnedBlogsScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    var blogs by remember { mutableStateOf<List<Blog>?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    suspend fun load() {
        if (!app.isSignedIn) {
            blogs = emptyList()
            return
        }
        try {
            blogs = app.client.ownedBlogs()
            failure = null
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            app.handleAuthError(error)
            failure = error
        }
    }
    LaunchedEffect(app.generation, app.server) { load() }
    PushedScreen(stringResource(R.string.me_my_blogs), background = colors.groupedBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val list = blogs
            val error = failure
            when {
                list == null && error != null -> LoadFailed(app.message(error)) { load() }
                list == null -> CircularProgressIndicator(Modifier.padding(40.dp))
                list.isEmpty() -> EmptyState(Icons.Rounded.Verified, stringResource(R.string.owned_none_title), stringResource(R.string.owned_none_description))
                else -> FormSection {
                    list.forEachIndexed { index, blog ->
                        BlogLine(blog, trailing = { Icon(Icons.Rounded.Verified, null, tint = colors.teal) }) { app.navigate(Route.BlogPage(blog.ref)) }
                        if (index < list.lastIndex) Hairline(Modifier.padding(start = 64.dp))
                    }
                }
            }
        }
    }
}

/** Points the app at another Explore server. Explore is open source; sign-ins are kept per server. */
@Composable
fun ServerScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(app.server) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun connect(input: String) {
        val normalized = Formatting.normalizedAddress(input)
        val uri = try {
            java.net.URI(normalized)
        } catch (_: Exception) {
            null
        }
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https") || uri.host.isNullOrEmpty()) {
            error = app.string(R.string.server_error_invalid)
            return
        }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val url = "$scheme://${uri.host}$port" + (uri.rawPath ?: "").trimEnd('/')
        if (url == app.server) {
            app.back()
            return
        }
        checking = true
        error = null
        scope.launch {
            try {
                ApiClient(url).siteConfig()
                app.switchServer(url)
                app.back()
            } catch (failure: Exception) {
                if (failure.isCancellation) throw failure
                error = app.string(R.string.server_error_not_explore)
            } finally {
                checking = false
            }
        }
    }
    PushedScreen(stringResource(R.string.me_server), background = colors.groupedBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FormSection(stringResource(R.string.server_address), stringResource(R.string.server_footer)) {
                FormField(address, { address = it }, "https://explore.example.com", keyboardType = KeyboardType.Uri, onDone = { connect(address) })
            }
            FormSection {
                SettingsRow(stringResource(R.string.server_connect), color = if (checking || address.isBlank()) colors.tertiaryLabel else colors.accent) {
                    if (!checking && address.isNotBlank()) connect(address)
                }
                if (!app.isDefaultServer) {
                    RowDivider(inset = false)
                    SettingsRow(stringResource(R.string.server_use_default), color = colors.accent) {
                        address = AppModel.DEFAULT_SERVER
                        connect(AppModel.DEFAULT_SERVER)
                    }
                }
            }
            if (checking) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            error?.let { FormError(it) }
        }
    }
}

/** What Explore is and the promises it keeps, in the app's own words. */
@Composable
fun AboutScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    PushedScreen(stringResource(R.string.about_title), background = colors.groupedBackground) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MarkTile(92.dp)
                    Text("Explore", style = ExploreType.largeTitle, color = colors.label)
                    Text(stringResource(R.string.about_tagline), style = ExploreType.title3.copy(fontWeight = FontWeight.Normal), color = colors.secondaryLabel)
                }
            }
            val points = listOf(
                Triple(Icons.Rounded.Explore, R.string.about_point_stream_title, R.string.about_point_stream),
                Triple(Icons.AutoMirrored.Rounded.OpenInNew, R.string.about_point_author_title, R.string.about_point_author),
                Triple(Icons.Rounded.Inbox, R.string.about_point_content_title, R.string.about_point_content),
                Triple(Icons.Rounded.BackHand, R.string.about_point_private_title, R.string.about_point_private),
                Triple(Icons.Rounded.Verified, R.string.about_point_listing_title, R.string.about_point_listing),
                Triple(Icons.AutoMirrored.Rounded.Logout, R.string.about_point_leaving_title, R.string.about_point_leaving),
            )
            items(points) { (icon, title, text) -> AboutPoint(icon, stringResource(title), stringResource(text)) }
            item {
                Column(Modifier.padding(top = 8.dp)) { FormSection {
                    LinkRow(stringResource(R.string.about_web), Icons.Rounded.Language) { app.openLink(app.server) }
                    RowDivider()
                    LinkRow(stringResource(R.string.about_source), Icons.Rounded.Code) { app.openLink("https://github.com/kite-plus/explore") }
                    RowDivider()
                    LinkRow(stringResource(R.string.about_crawler), Icons.Rounded.BugReport) { app.openLink(app.client.botUrl) }
                } }
            }
            item {
                Text(stringResource(R.string.about_footer), style = ExploreType.footnote, color = colors.secondaryLabel, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun AboutPoint(icon: ImageVector, title: String, text: String) {
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = colors.label)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = ExploreType.headline, color = colors.label)
            Text(text, style = ExploreType.subheadline, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun LinkRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = Explore.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = colors.secondaryLabel)
        Text(title, style = ExploreType.body, color = colors.label, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.tertiaryLabel)
    }
}
