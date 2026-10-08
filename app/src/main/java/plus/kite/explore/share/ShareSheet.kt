package plus.kite.explore.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import plus.kite.explore.R
import plus.kite.explore.account.shareFile
import plus.kite.explore.account.sharedFile
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.ShareSubject
import plus.kite.explore.core.Formatting
import plus.kite.explore.core.ImageLoader
import plus.kite.explore.core.QrCode
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.BlogAvatarFace
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreSheet
import plus.kite.explore.design.ExploreTheme
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.KiteBlue
import plus.kite.explore.design.KiteMark
import plus.kite.explore.design.SheetBar

private const val CARD_WIDTH = 360

/** What a card shows once its pieces have loaded. */
private sealed interface CardContent {
    data class Post(val entry: Entry, val blog: BlogRef?, val favicon: ImageBitmap?) : CardContent
    data class Blog(val blog: BlogRef, val about: String, val recent: List<Entry>, val favicon: ImageBitmap?) : CardContent
}

/** A card to share a post or a blog: a preview of the image, and ways to save it, send it on, or copy the link. */
@Composable
fun ShareSheet(subject: ShareSubject, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var content by remember { mutableStateOf<CardContent?>(null) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    // The card's code and Copy Link lead to the post's share page, or the blog's page on Explore.
    val link = when (subject) {
        is ShareSubject.Post -> app.client.webPostUrl(subject.entry.id)
        is ShareSubject.Blog -> app.client.webBlogUrl(subject.blog.host)
    }
    val code = remember(link) { QrCode.encode(link) }
    val title = when (subject) {
        is ShareSubject.Post -> subject.entry.title
        is ShareSubject.Blog -> subject.blog.name
    }

    LaunchedEffect(subject) {
        val pixels = with(density) { 64.dp.roundToPx() }
        content = when (subject) {
            is ShareSubject.Post -> {
                val favicon = subject.blog?.let { ImageLoader.load(app.client.faviconUrl(it.host), pixels, followsServerCache = true) }
                CardContent.Post(subject.entry, subject.blog, favicon?.takeIf { it.width > 1 }?.asImageBitmap())
            }
            is ShareSubject.Blog -> {
                val page = try {
                    app.client.blog(subject.blog.host, null)
                } catch (error: Exception) {
                    if (error.isCancellation) throw error
                    null
                }
                val favicon = ImageLoader.load(app.client.faviconUrl(subject.blog.host), pixels, followsServerCache = true)
                CardContent.Blog(
                    page?.blog?.ref ?: subject.blog, page?.blog?.about.orEmpty(), page?.entries.orEmpty(),
                    favicon?.takeIf { it.width > 1 }?.asImageBitmap(),
                )
            }
        }
        // One frame for the card to draw before it is captured.
        delay(150)
        card = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    }

    fun flash(set: (Boolean) -> Unit) {
        set(true)
        scope.launch {
            delay(1600)
            set(false)
        }
    }

    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.action_share), onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
                val scale = minOf(1f, maxWidth.value / CARD_WIDTH)
                val shown = content
                if (shown == null) {
                    Box(Modifier.height(360.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Box(
                        Modifier
                            .wrapContentHeight()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            }
                            .shadow(16.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                    ) {
                        ExploreTheme(dark = false) {
                            when (shown) {
                                is CardContent.Post -> PostCard(shown, code, app.sourceTag)
                                is CardContent.Blog -> BlogCard(shown, code, app.sourceTag)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareAction(stringResource(if (saved) R.string.share_saved else R.string.share_save), if (saved) Icons.Rounded.Check else Icons.Rounded.Download, card != null, Modifier.weight(1f)) {
                    val image = card ?: return@ShareAction
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveToPhotos(context, image, title) }
                        if (ok) flash { saved = it } else app.showToast(app.string(R.string.share_save_failed), Icons.Rounded.Download, isError = true)
                    }
                }
                ShareAction(stringResource(R.string.share_image), Icons.Rounded.Share, card != null, Modifier.weight(1f)) {
                    val image = card ?: return@ShareAction
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { sharedFile(context, "Kite.png", png(image)) }
                        shareFile(context, uri, "image/png")
                    }
                }
                ShareAction(stringResource(if (copied) R.string.share_copied else R.string.action_copy_link), if (copied) Icons.Rounded.Check else Icons.Rounded.Link, true, Modifier.weight(1f)) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(title, link))
                    flash { copied = it }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Explore.colors
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.card, contentColor = colors.label),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(title, style = ExploreType.footnote.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

private fun png(image: Bitmap): ByteArray = ByteArrayOutputStream().use {
    image.compress(Bitmap.CompressFormat.PNG, 100, it)
    it.toByteArray()
}

/** Adds the card to the photo library; Android 10 and later need no permission for this. */
private fun saveToPhotos(context: Context, image: Bitmap, title: String): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Kite ${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.TITLE, title)
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kite")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

/**
 * The parts every share card has: the Kite mark on top, the card's own content, then a dashed
 * line over the code and where it leads. The card keeps one look whatever the app's appearance,
 * as an image passed around should.
 */
@Composable
private fun CardFrame(code: QrCode, caption: String, site: String, content: @Composable () -> Unit) {
    val ink = Color(0xFF111111)
    Column(
        Modifier
            .requiredWidth(CARD_WIDTH.dp)
            .background(Color.White)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            KiteMark(Modifier.size(width = 24.dp, height = 20.dp))
            Text("Kite", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
        content()
        Canvas(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 18.dp).height(1.dp)) {
            drawLine(
                Color.Black.copy(alpha = 0.18f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QrImage(code, Modifier.size(88.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(caption, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Text(site, fontSize = 13.sp, color = Color(0x993C3C43))
            }
        }
    }
}

/** A QR code drawn module by module, without smoothing. */
@Composable
private fun QrImage(code: QrCode, modifier: Modifier) {
    Canvas(modifier) {
        val module = size.width / code.size
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code.isDark(x, y)) drawRect(Color.Black, Offset(x * module, y * module), Size(module + 0.5f, module + 0.5f))
            }
        }
    }
}

/** A post as a card: the blog and the day it came out, the title and the excerpt. */
@Composable
private fun PostCard(content: CardContent.Post, code: QrCode, site: String) {
    val secondary = Color(0x993C3C43)
    CardFrame(code, stringResource(R.string.share_scan_post), site) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.blog?.let {
                    BlogAvatarFace(it.host, it.name, 24.dp, content.favicon)
                    Text(it.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                content.entry.publishedAt?.let {
                    if (content.blog != null) Text("·", fontSize = 14.sp, color = Color(0x4D3C3C43))
                    Text(Formatting.day(it), fontSize = 14.sp, color = secondary)
                }
            }
            Text(content.entry.title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), maxLines = 5, overflow = TextOverflow.Ellipsis)
            val excerpt = content.entry.excerpt?.trim().orEmpty()
            if (excerpt.isNotEmpty()) {
                Text(excerpt, fontSize = 15.sp, lineHeight = 21.sp, color = secondary, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A blog as a card: who it is, what it writes about and its latest posts. */
@Composable
private fun BlogCard(content: CardContent.Blog, code: QrCode, site: String) {
    val secondary = Color(0x993C3C43)
    CardFrame(code, stringResource(R.string.share_scan_blog), site) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlogAvatarFace(content.blog.host, content.blog.name, 64.dp, content.favicon, Modifier.padding(bottom = 4.dp))
                Text(content.blog.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), maxLines = 2, textAlign = TextAlign.Center)
                Text(content.blog.host, fontSize = 14.sp, color = secondary)
                if (content.about.isNotEmpty()) {
                    Text(content.about, fontSize = 15.sp, lineHeight = 21.sp, color = secondary, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (content.recent.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.035f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.blog_recent_posts), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                    for (entry in content.recent.take(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(KiteBlue))
                            Text(entry.title, fontSize = 15.sp, lineHeight = 20.sp, color = Color(0xFF111111), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
