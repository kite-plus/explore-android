package plus.kite.explore.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import plus.kite.explore.R

/** A small capsule of secondary information, such as a blog's language. */
@Composable
fun Pill(text: String, icon: ImageVector? = null, tint: Color = Explore.colors.secondaryLabel) {
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = tint)
        Text(text, style = ExploreType.caption.copy(fontWeight = FontWeight.Medium), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The app's main actions: black in light mode and white in dark, so color stays with the content. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = Explore.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 52.dp else 44.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.label,
            contentColor = colors.background,
            disabledContainerColor = colors.label.copy(alpha = 0.25f),
            disabledContentColor = colors.background.copy(alpha = 0.8f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (large) 14.dp else 10.dp),
        content = content,
    )
}

/** A quiet action beside a main one, on a gray fill. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = Explore.colors
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 52.dp else 44.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.fill, contentColor = colors.label),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = if (large) 14.dp else 10.dp),
        content = content,
    )
}

@Composable
fun KiteMark(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(painterResource(R.drawable.kite_mark), contentDescription = null, modifier = modifier, tint = tint)
}

/** The app icon as a tile, for sign-in and about screens. */
@Composable
fun MarkTile(size: Dp = 88.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.verticalGradient(listOf(Color(0xFF5E8BE6), Color(0xFF3D66C2)))),
        contentAlignment = Alignment.Center,
    ) {
        KiteMark(Modifier.width(size * 0.5f).height(size * 0.41f), tint = Color.White)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { heading() },
        style = ExploreType.title3.copy(fontWeight = FontWeight.Bold),
        color = Explore.colors.label,
    )
}

/** A hairline between rows, as iOS lists draw them. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.5.dp, color = Explore.colors.separator)
}

/** An icon, a title and a sentence for a list with nothing to show, with optional actions. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = Explore.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(44.dp), tint = colors.secondaryLabel)
        Spacer(Modifier.height(4.dp))
        Text(title, style = ExploreType.title3.copy(fontWeight = FontWeight.Bold), color = colors.label, textAlign = TextAlign.Center)
        Text(description, style = ExploreType.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        if (actions != null) {
            Spacer(Modifier.height(8.dp))
            actions()
        }
    }
}

/** Shown in place of a list that could not load. */
@Composable
fun LoadFailed(message: String, modifier: Modifier = Modifier, retry: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    EmptyState(Icons.Rounded.WifiOff, stringResource(R.string.load_failed_title), message, modifier) {
        PrimaryButton(
            onClick = {
                scope.launch {
                    retrying = true
                    retry()
                    retrying = false
                }
            },
            enabled = !retrying,
        ) {
            if (retrying) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Explore.colors.background, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.action_try_again))
            }
        }
    }
}

/** The line under a list that has nothing more to load. */
@Composable
fun ListEnd(text: String) {
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(0.5.dp).background(colors.separator))
        Text(text, style = ExploreType.footnote, color = colors.secondaryLabel)
        Box(Modifier.weight(1f).height(0.5.dp).background(colors.separator))
    }
}

/** Loads the next page when it comes into view, or offers a retry. */
@Composable
fun PageFooter(
    isLoading: Boolean,
    error: String?,
    hasMore: Boolean,
    itemCount: Int,
    endText: String?,
    loadMore: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error, style = ExploreType.footnote, color = Explore.colors.secondaryLabel, textAlign = TextAlign.Center)
                SecondaryButton(onClick = { scope.launch { loadMore() } }) { Text(stringResource(R.string.action_try_again)) }
            }
            !hasMore && endText != null -> ListEnd(endText)
        }
    }
    LaunchedEffect(itemCount, hasMore) {
        if (hasMore && !isLoading && error == null) loadMore()
    }
}

/** A soft highlight sweeping across placeholders. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val highlight = if (Explore.colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.5f)
    return drawWithContent {
        drawContent()
        val width = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(phase * width - width * 0.3f, 0f),
                end = androidx.compose.ui.geometry.Offset(phase * width + width * 0.3f, 0f),
            ),
        )
    }
}

/** A gray block standing in for text while a page loads. */
@Composable
fun PlaceholderBlock(width: Dp, height: Dp, modifier: Modifier = Modifier, radius: Dp = 4.dp) {
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(Explore.colors.fill),
    )
}

/** A gray post in the shape of a row, while the first page loads. */
@Composable
fun EntryPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .shimmer(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Explore.colors.fill))
                PlaceholderBlock(140.dp, 12.dp)
            }
            PlaceholderBlock(240.dp, 18.dp)
            PlaceholderBlock(180.dp, 18.dp)
            PlaceholderBlock(220.dp, 13.dp)
        }
        PlaceholderBlock(96.dp, 64.dp, radius = 10.dp)
    }
}

/** A transient message near the top of the screen. */
data class Toast(val message: String, val icon: ImageVector, val isError: Boolean = false, val id: Long = System.nanoTime())

@Composable
fun ToastView(toast: Toast, modifier: Modifier = Modifier) {
    val colors = Explore.colors
    Surface(
        modifier = modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp),
        shape = CircleShape,
        color = colors.card,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(toast.icon, null, Modifier.size(20.dp), tint = if (toast.isError) colors.orange else colors.label)
            Text(
                toast.message,
                style = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
