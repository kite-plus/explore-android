package plus.kite.explore.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import plus.kite.explore.R
import plus.kite.explore.app.Handoff
import plus.kite.explore.app.LocalApp
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.Entry
import plus.kite.explore.data.LinkStatus
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.KiteMark
import plus.kite.explore.design.Pill
import plus.kite.explore.design.PrimaryButton

/**
 * The result of Explore's latest check of a post's link: a green check, "Possibly
 * unavailable" for a 404 or 410, or quiet gray text while it waits for one, as on the website.
 * Tapping shows the details. Compact, as in a narrow byline, a link still waiting shows only its icon.
 */
@Composable
fun LinkStatusBadge(entry: Entry, compact: Boolean = false) {
    val app = LocalApp.current
    val colors = Explore.colors
    val (status, checkedAt) = app.linkStatus(entry)
    val checking = entry.id in app.checkingLinks
    var details by remember { mutableStateOf(false) }
    val title = statusTitle(status, checkedAt != null)
    Box {
        Row(
            modifier = Modifier
                .clickable { details = true }
                .padding(vertical = 4.dp)
                .semantics { contentDescription = title },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            when {
                checking -> {
                    CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = colors.secondaryLabel)
                    Text(stringResource(R.string.link_checking), style = ExploreType.footnote, color = colors.secondaryLabel, maxLines = 1)
                }
                status == LinkStatus.Available -> Icon(Icons.Rounded.CheckCircleOutline, null, Modifier.size(15.dp), tint = colors.green)
                status == LinkStatus.Unavailable -> Pill(stringResource(R.string.link_possibly_unavailable), Icons.Rounded.Cancel, colors.red)
                else -> {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(14.dp), tint = colors.secondaryLabel)
                    if (!compact) Text(title, style = ExploreType.footnote, color = colors.secondaryLabel, maxLines = 1)
                }
            }
        }
        if (details) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 72),
                onDismissRequest = { details = false },
                properties = PopupProperties(focusable = true),
            ) {
                LinkStatusDetails(entry)
            }
        }
    }
}

@Composable
private fun statusTitle(status: LinkStatus, checked: Boolean): String = stringResource(
    when (status) {
        LinkStatus.Available -> R.string.link_last_check_succeeded
        LinkStatus.Unavailable -> R.string.link_possibly_unavailable
        LinkStatus.Unknown -> if (checked) R.string.link_status_uncertain else R.string.link_awaiting_check
    },
)

@Composable
private fun LinkStatusDetails(entry: Entry) {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    val (status, checkedAt) = app.linkStatus(entry)
    val checking = entry.id in app.checkingLinks
    val (icon, tint) = when (status) {
        LinkStatus.Available -> Icons.Rounded.CheckCircle to colors.green
        LinkStatus.Unavailable -> Icons.Rounded.Cancel to colors.red
        LinkStatus.Unknown -> Icons.AutoMirrored.Rounded.Help to colors.orange
    }
    Surface(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.card,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                Text(statusTitle(status, checkedAt != null), style = ExploreType.headline, color = colors.label)
            }
            Text(
                if (checkedAt != null) stringResource(R.string.link_checked_at, Formatting.relative(context, checkedAt)) else stringResource(R.string.link_not_checked),
                style = ExploreType.subheadline,
                color = colors.secondaryLabel,
            )
            if (status == LinkStatus.Unavailable) {
                Text(stringResource(R.string.link_unavailable_detail), style = ExploreType.footnote, color = colors.secondaryLabel)
            }
            Text(stringResource(R.string.link_checked_by_explore), style = ExploreType.footnote, color = colors.tertiaryLabel)
            if (status == LinkStatus.Unknown) {
                PrimaryButton(onClick = { app.checkLink(entry) }, modifier = Modifier.fillMaxWidth(), enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.background)
                        Text("  " + stringResource(R.string.link_checking))
                    } else {
                        Text(stringResource(R.string.link_check_now))
                    }
                }
            }
        }
    }
}

/**
 * What the app shows for a moment before a post opens, as the website's transition page
 * does: where the reader is going, the blog and the post, and a bar that fills until the page
 * opens.
 */
@Composable
fun HandoffOverlay(handoff: Handoff) {
    val app = LocalApp.current
    val colors = Explore.colors
    val progress = remember(handoff.id) { Animatable(0f) }
    val host = Formatting.hostOf(handoff.url) ?: handoff.url
    LaunchedEffect(handoff.id) {
        progress.animateTo(1f, tween(HANDOFF_MILLIS, easing = FastOutSlowInEasing))
        app.openPostNow(handoff.entry, handoff.blog)
        // The page goes once the browser tab covers it.
        delay(500)
        app.endHandoff(handoff.id)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KiteMark(Modifier.size(width = 34.dp, height = 28.dp))
            Text(
                stringResource(R.string.handoff_title),
                style = ExploreType.subheadline,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(top = 32.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                handoff.blog?.let { BlogAvatar(it.host, it.name, 40.dp) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(handoff.blog?.name ?: host, style = ExploreType.headline, color = colors.label, maxLines = 1)
                    Text(host, style = ExploreType.subheadline, color = colors.secondaryLabel, maxLines = 1)
                }
            }
            Text(
                handoff.title,
                style = ExploreType.subheadline,
                color = colors.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 20.dp),
            )
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .width(160.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.fill),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).clip(CircleShape).background(colors.accent))
            }
        }
    }
}

/** How long the page shows before the post opens, as on the website. */
const val HANDOFF_MILLIS = 700
