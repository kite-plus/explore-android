package plus.kite.explore.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object Palette {
    /** The website's avatar colors (Tailwind 600s), in the same order, so a blog gets one color everywhere. */
    val avatars: List<Color> = listOf(
        0xFFE11D48, 0xFFEA580C, 0xFFD97706, 0xFF059669, 0xFF0D9488,
        0xFF0284C7, 0xFF4F46E5, 0xFF7C3AED, 0xFFC026D3, 0xFF475569,
    ).map { Color(it) }

    fun color(host: String): Color = avatars[avatarIndex(host)]

    /** Mirrors avatarColor in the web's lib/avatar.ts: a 32-bit hash over code points. */
    fun avatarIndex(host: String): Int {
        var hash = 0u
        var i = 0
        while (i < host.length) {
            val point = host.codePointAt(i)
            hash = hash * 31u + point.toUInt()
            i += Character.charCount(point)
        }
        return (hash % avatars.size.toUInt()).toInt()
    }

    fun initial(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        val end = trimmed.offsetByCodePoints(0, 1)
        return trimmed.substring(0, end).uppercase()
    }
}

/** The icon shown for each topic. */
object TopicStyle {
    fun icon(slug: String): ImageVector = when (slug) {
        "frontend" -> Icons.Rounded.Code
        "backend" -> Icons.Rounded.Dns
        "mobile" -> Icons.Rounded.Smartphone
        "ai" -> Icons.Rounded.AutoAwesome
        "data" -> Icons.Rounded.BarChart
        "ops" -> Icons.Rounded.Cloud
        "security" -> Icons.Rounded.Security
        "languages" -> Icons.Rounded.DataObject
        "tools" -> Icons.Rounded.Handyman
        "design" -> Icons.Rounded.Palette
        "product" -> Icons.Rounded.Lightbulb
        "career" -> Icons.Rounded.Work
        "life" -> Icons.Rounded.Eco
        "reading" -> Icons.AutoMirrored.Rounded.MenuBook
        "travel" -> Icons.Rounded.Flight
        else -> Icons.Rounded.Tag
    }
}
