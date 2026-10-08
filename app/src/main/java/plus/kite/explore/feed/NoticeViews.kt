package plus.kite.explore.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import plus.kite.explore.R
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.DiscoverStream
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Route
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.Entry
import plus.kite.explore.data.Notice
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.NoticeAvatar
import plus.kite.explore.design.NoticeMark
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.SecondaryButton

/**
 * A notice or ad between the posts, laid out as a post is, with a mark where a post gives its
 * time: a warm capsule for a notice, an outlined one for an ad, and a pin when it leads.
 */
@Composable
fun NoticeRow(notice: Notice) {
    val app = LocalApp.current
    val colors = Explore.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // A link opens as posts do; an ad's carries utm_source. One without a link is Explore's own text.
                if (notice.url.isNotEmpty()) {
                    app.openLink(notice.url, if (notice.isNotice) null else app.sourceTag)
                } else {
                    app.navigate(Route.NoticePage(notice.id, notice))
                }
            }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NoticeAvatar(notice, 18.dp)
            Text(
                notice.sourceName,
                style = ExploreType.footnote.copy(fontWeight = FontWeight.Medium),
                color = colors.label.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("·", style = ExploreType.footnote, color = colors.secondaryLabel)
            NoticeMark(notice)
        }
        Text(notice.title, style = ExploreType.headline, color = colors.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (notice.summary.isNotEmpty()) {
            Text(notice.summary, style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A notice or ad's own page, drawn by the app and laid out as an article, as on the website:
 * who it is from with its date and mark, the title and lead, the plain text in paragraphs with
 * its addresses linked, a card on who published it, and the latest posts to go on to.
 */
@Composable
fun NoticeScreen(id: String, preview: Notice?) {
    val app = LocalApp.current
    val context = LocalContext.current
    var notice by remember(id) { mutableStateOf<Notice?>(null) }
    var failure by remember(id) { mutableStateOf<Throwable?>(null) }
    var gone by remember(id) { mutableStateOf(false) }
    var latest by remember(id) { mutableStateOf<List<Entry>>(emptyList()) }

    suspend fun load() {
        failure = null
        try {
            notice = app.client.notice(id)
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            gone = (error as? ApiError)?.status == 404
            failure = error
        }
    }

    LaunchedEffect(id, app.server) {
        coroutineScope {
            val posts = async {
                try {
                    app.client.entries(null, null, null).data.take(3)
                } catch (error: Exception) {
                    if (error.isCancellation) throw error
                    emptyList()
                }
            }
            load()
            latest = posts.await()
        }
    }

    val shown = if (gone) null else notice ?: preview
    PushedScreen(
        title = "",
        actions = {
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, app.client.webNoticeUrl(id))
                context.startActivity(Intent.createChooser(send, null))
            }) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val error = failure
            when {
                shown != null -> NoticeArticle(shown, notice?.body, error, latest) { load() }
                error != null -> LoadFailed(
                    if (gone) stringResource(R.string.notice_gone) else app.message(error),
                    Modifier.padding(top = 80.dp),
                ) { load() }
                else -> CircularProgressIndicator(Modifier.padding(top = 120.dp))
            }
        }
    }
}

@Composable
private fun NoticeArticle(notice: Notice, body: String?, failure: Throwable?, latest: List<Entry>, retry: suspend () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NoticeAvatar(notice, 40.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(notice.sourceName, style = ExploreType.headline, color = colors.label, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    notice.publishedAt?.let {
                        Text(Formatting.day(it), style = ExploreType.subheadline, color = colors.secondaryLabel)
                        Text("·", style = ExploreType.subheadline, color = colors.secondaryLabel)
                    }
                    NoticeMark(notice)
                }
            }
        }
        Text(notice.title, style = ExploreType.title, color = colors.label, modifier = Modifier.padding(top = 22.dp))
        if (notice.summary.isNotEmpty()) {
            Text(notice.summary, style = ExploreType.title3.copy(fontWeight = FontWeight.Normal), color = colors.secondaryLabel, modifier = Modifier.padding(top = 12.dp))
        }
        when {
            body != null -> {
                val paragraphs = paragraphs(body)
                if (paragraphs.isNotEmpty()) {
                    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Hairline(Modifier.padding(bottom = 6.dp))
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                for (paragraph in paragraphs) {
                                    Text(linked(paragraph, colors.accent) { app.openLink(it) }, style = ExploreType.body.copy(lineHeight = 29.sp), color = colors.label)
                                }
                            }
                        }
                    }
                }
            }
            failure != null -> LoadFailed(app.message(failure), retry = retry)
            notice.url.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (notice.url.isNotEmpty() && notice.isNotice) {
            PrimaryButton(onClick = { app.openLink(notice.url) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), large = true) {
                Text(stringResource(R.string.notice_open_link), style = ExploreType.headline)
            }
        }
        SourceCard(notice, Modifier.padding(top = 36.dp))
        if (latest.isNotEmpty()) {
            Column(Modifier.padding(top = 36.dp)) {
                Text(
                    stringResource(R.string.notice_keep_exploring),
                    style = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Hairline()
                for (entry in latest) {
                    EntryRow(entry)
                    Hairline()
                }
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable {
                            app.stream = DiscoverStream.Latest
                            app.back()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.notice_see_all_latest), style = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.label)
                }
            }
        }
    }
}

/** Kite Plus for a notice; the advertiser, marked as such, for an ad. */
@Composable
private fun SourceCard(notice: Notice, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = Explore.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.secondaryBackground)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NoticeAvatar(notice, 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (notice.isNotice) "Kite Plus" else notice.sourceName, style = ExploreType.headline, color = colors.label)
                Text(
                    stringResource(if (notice.isNotice) R.string.notice_kite_plus_about else R.string.notice_ad_about),
                    style = ExploreType.subheadline,
                    color = colors.secondaryLabel,
                )
            }
        }
        if (notice.isNotice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(onClick = { app.sheet = AppSheet.Submit() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.submit_title), maxLines = 1)
                }
                SecondaryButton(onClick = { app.navigate(Route.About) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.about_explore), maxLines = 1)
                }
            }
        } else if (notice.url.isNotEmpty()) {
            PrimaryButton(onClick = { app.openLink(notice.url, app.sourceTag) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notice_open_link))
            }
        }
    }
}

/** The text's paragraphs: split at blank lines, with single line breaks kept inside, as the website shows them. */
fun paragraphs(text: String): List<String> {
    val result = mutableListOf<String>()
    val current = mutableListOf<String>()
    for (line in text.replace("\r\n", "\n").split('\n')) {
        if (line.isBlank()) {
            if (current.isNotEmpty()) result += current.joinToString("\n").trim()
            current.clear()
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) result += current.joinToString("\n").trim()
    return result.filter { it.isNotEmpty() }
}

private val webAddress = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'，。、；：！？）】」』]+)""")

/** A paragraph with its web addresses as links, www. ones included; they open as other links do. */
fun linked(paragraph: String, color: androidx.compose.ui.graphics.Color, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in webAddress.findAll(paragraph)) {
        val raw = match.value.trimEnd('.', ',', ')', ';', ':', '!', '?')
        val start = match.range.first
        val end = start + raw.length
        append(paragraph.substring(last, start))
        val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        pushLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { open(url) })
        append(raw)
        pop()
        last = end
    }
    append(paragraph.substring(last))
}
