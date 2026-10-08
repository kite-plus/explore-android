package plus.kite.explore.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads post thumbnails and blog icons from Explore's own image endpoints. Images are
 * downsampled to the size they are shown at and kept in memory, so rows that scroll back
 * into view draw at once; the HTTP cache keeps the files on disk.
 */
object ImageLoader {
    private val memory = object : LruCache<String, Bitmap>(96 shl 20) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()
    private val failures = ConcurrentHashMap<String, Long>()

    private fun key(url: String, pixels: Int) = "$url#$pixels"

    fun cached(url: String, pixels: Int): Bitmap? = memory.get(key(url, pixels))

    /** True when the image failed recently; Explore fetches images on demand, so failures are retried later. */
    fun hasFailed(url: String): Boolean {
        val at = failures[url] ?: return false
        return System.currentTimeMillis() - at < 5 * 60 * 1000
    }

    /**
     * Set [followsServerCache] for images that may change, such as blog icons: Explore answers
     * a missing icon with a placeholder it keeps for an hour, which must not stick on the device.
     */
    suspend fun load(url: String, pixels: Int, followsServerCache: Boolean = false): Bitmap? {
        val key = key(url, pixels)
        memory.get(key)?.let { return it }
        val pending = CompletableDeferred<Bitmap?>()
        val existing = inflight.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        val image = try {
            fetch(url, pixels, followsServerCache)
        } catch (_: Exception) {
            null
        }
        if (image != null) {
            memory.put(key, image)
            failures.remove(url)
        } else {
            failures[url] = System.currentTimeMillis()
        }
        pending.complete(image)
        inflight.remove(key)
        return image
    }

    private suspend fun fetch(url: String, pixels: Int, followsServerCache: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.useCaches = true
            // Post images change rarely: reuse what is on disk instead of asking again.
            if (!followsServerCache) connection.addRequestProperty("Cache-Control", "max-stale=604800")
            if (connection.responseCode !in 200..299) return@withContext null
            val data = connection.inputStream.use { it.readBytes() }
            decode(data, pixels)
        } finally {
            connection.disconnect()
        }
    }

    /** Decodes at most about [pixels] on the long edge. */
    fun decode(data: ByteArray, pixels: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= pixels) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }
}

/** An image for the given address, null until it loads or when it cannot be had. */
@Composable
fun rememberImage(url: String?, pixels: Int, followsServerCache: Boolean = false): State<ImageBitmap?> =
    produceState(initialValue = url?.let { ImageLoader.cached(it, pixels)?.asImageBitmap() }, url, pixels) {
        if (url == null || value != null) return@produceState
        value = ImageLoader.load(url, pixels, followsServerCache)?.asImageBitmap()
    }
