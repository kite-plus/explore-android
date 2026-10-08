package plus.kite.explore.design

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import plus.kite.explore.R
import plus.kite.explore.app.LocalApp
import plus.kite.explore.core.rememberImage
import plus.kite.explore.data.Notice

/**
 * A blog's icon: its first letter on its color, covered by the site's favicon once Explore has
 * fetched it, as the website shows it. Explore answers a 1x1 transparent image when a blog has
 * no icon, which leaves the letter showing.
 */
@Composable
fun BlogAvatar(host: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val favicon by rememberImage(app.client.faviconUrl(host), pixels, followsServerCache = true)
    BlogAvatarFace(host, name, size, favicon?.takeIf { it.width > 1 }, modifier)
}

/** What a blog avatar draws for a favicon already in hand, so a share card can draw one too. */
@Composable
fun BlogAvatarFace(host: String, name: String, size: Dp, favicon: ImageBitmap?, modifier: Modifier = Modifier) {
    val color = Palette.color(host)
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Palette.initial(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = with(density) { (size * 0.46f).toSp() },
            maxLines = 1,
        )
        Crossfade(favicon, label = "favicon") { icon ->
            if (icon != null) {
                Box(Modifier.size(size).background(Color.White), contentAlignment = Alignment.Center) {
                    val side = iconSide(icon, size, density.density)
                    Image(icon, null, Modifier.size(side), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                }
            }
        }
        if (favicon != null && size >= 64.dp) {
            // A large white disc needs an edge against a white page.
            Box(Modifier.size(size).border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape))
        }
    }
}

/**
 * How wide the favicon is drawn: nearly the whole circle, unless the avatar is large and the
 * favicon too small to fill it without blurring; then it sits in the middle, enlarged at most
 * three times.
 */
private fun iconSide(icon: ImageBitmap, size: Dp, density: Float): Dp {
    val full = size * 0.76f
    if (size < 64.dp) return full
    val width = icon.width.toFloat()
    if (width >= full.value * density * 0.6f) return full
    val side = (width / density * 3).dp
    return side.coerceIn(size * 0.34f, size * 0.5f)
}

/** A post's thumbnail. It removes itself when the image cannot be loaded. */
@Composable
fun EntryThumbnail(url: String, width: Dp, height: Dp, modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val pixels = with(LocalDensity.current) { maxOf(width, height).roundToPx() * 2 }
    val image by rememberImage(url, pixels)
    if (image == null && plus.kite.explore.core.ImageLoader.hasFailed(url)) return
    val colors = Explore.colors
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(radius))
            .background(colors.fill)
            .border(1.dp, colors.label.copy(alpha = 0.06f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, null, Modifier.size(width, height), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Image, null, Modifier.size(22.dp), tint = colors.tertiaryLabel)
        }
    }
}

/** The Kite Plus mark for a notice, or the advertiser's initial for an ad. */
@Composable
fun NoticeAvatar(notice: Notice, size: Dp = 18.dp) {
    if (notice.isNotice) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(Color.White)
                .border(1.dp, Explore.colors.quaternaryLabel, RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            KiteMark(Modifier.size(size * 0.56f, size * 0.46f))
        }
    } else {
        BlogAvatarFace(notice.sourceName, notice.sourceName, size, null)
    }
}

/** "Notice" on a warm capsule or "Ad" on an outlined one, pinned or not. */
@Composable
fun NoticeMark(notice: Notice) {
    val colors = Explore.colors
    val label = stringResource(
        when {
            notice.isNotice && notice.isPinned -> R.string.notice_pinned_notice
            notice.isNotice -> R.string.notice_notice
            notice.isPinned -> R.string.notice_pinned_ad
            else -> R.string.notice_ad
        },
    )
    val tint = if (notice.isNotice) colors.orange else colors.secondaryLabel
    val shape = CircleShape
    val base = Modifier.semantics { contentDescription = label }
    val decorated = if (notice.isNotice) {
        base.background(colors.orange.copy(alpha = 0.14f), shape)
    } else {
        base.border(1.dp, colors.tertiaryLabel, shape)
    }
    Row(decorated.padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        if (notice.isPinned) {
            Icon(Icons.Rounded.PushPin, null, Modifier.size(10.dp), tint = tint)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            stringResource(if (notice.isNotice) R.string.notice_notice else R.string.notice_ad),
            color = tint,
            style = ExploreType.caption2.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
