package plus.kite.explore.core

import android.content.Context
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import plus.kite.explore.R

object Formatting {
    /** "3 hours ago" within a week and a date after that, as the website shows. */
    fun relative(context: Context, instant: Instant, now: Instant = Instant.now()): String {
        val elapsed = Duration.between(instant, now).seconds
        if (elapsed < 0 || elapsed >= 7 * 24 * 3600) return day(instant, now)
        if (elapsed < 60) return context.getString(R.string.time_just_now)
        val formatter = RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(Locale.getDefault()), null,
            RelativeDateTimeFormatter.Style.LONG, DisplayContext.CAPITALIZATION_NONE,
        )
        val last = RelativeDateTimeFormatter.Direction.LAST
        return when {
            elapsed < 3600 -> formatter.format((elapsed / 60).toDouble(), last, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
            elapsed < 24 * 3600 -> formatter.format((elapsed / 3600).toDouble(), last, RelativeDateTimeFormatter.RelativeUnit.HOURS)
            elapsed < 48 * 3600 -> formatter.format(last, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            else -> formatter.format((elapsed / (24 * 3600)).toDouble(), last, RelativeDateTimeFormatter.RelativeUnit.DAYS)
        }
    }

    /** A calendar date, with the year only when it is not this year. */
    fun day(instant: Instant, now: Instant = Instant.now()): String {
        val zone = ZoneId.systemDefault()
        val sameYear = instant.atZone(zone).year == now.atZone(zone).year
        val format = DateFormat.getInstanceForSkeleton(if (sameYear) "MMMd" else "yMMMd", Locale.getDefault())
        return format.format(Date.from(instant))
    }

    fun dateTime(instant: Instant): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date.from(instant))

    /** The language a blog declares, named in the interface language. */
    fun languageName(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        val locale = Locale.forLanguageTag(trimmed)
        val name = locale.getDisplayLanguage(Locale.getDefault())
        return name.ifEmpty { trimmed }
    }

    fun generatorName(context: Context, raw: String): String? = when (raw.lowercase()) {
        "wordpress" -> "WordPress"
        "halo" -> "Halo"
        "hugo" -> "Hugo"
        "hexo" -> "Hexo"
        "typecho" -> "Typecho"
        "jekyll" -> "Jekyll"
        "ghost" -> "Ghost"
        "kite" -> "Kite"
        "other" -> context.getString(R.string.generator_other)
        else -> null
    }

    /** A host for display, without "www.". */
    fun displayHost(host: String): String = host.removePrefix("www.")

    private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** Accepts what people type into an address field: adds https:// when the scheme is missing. */
    fun normalizedAddress(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (schemePattern.containsMatchIn(trimmed)) return trimmed
        return "https://$trimmed"
    }

    /** The host of an address, lowercased, or null when it has none. */
    fun hostOf(address: String): String? = try {
        java.net.URI(address).host?.lowercase()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** Whether an address is a web page; other schemes would hand the tap to whatever app claims them. */
    fun isWeb(address: String): Boolean {
        val scheme = address.substringBefore(':', "").lowercase()
        return (scheme == "http" || scheme == "https") && hostOf(address) != null
    }
}

object SourceTag {
    /**
     * Adds utm_source so the author's analytics can tell a visit came from Explore, as the
     * website does. The rest of the address is untouched and an existing utm_source wins.
     */
    fun tagged(url: String, source: String): String {
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") return url
        val hash = url.indexOf('#')
        val beforeHash = if (hash >= 0) url.substring(0, hash) else url
        val fragment = if (hash >= 0) url.substring(hash) else ""
        val questionMark = beforeHash.indexOf('?')
        val query = if (questionMark >= 0) beforeHash.substring(questionMark + 1) else ""
        if (query.split('&').any { it.substringBefore('=') == "utm_source" }) return url
        val parameter = "utm_source=" + encode(source)
        val joined = when {
            questionMark < 0 -> "$beforeHash?$parameter"
            query.isEmpty() -> "$beforeHash$parameter"
            else -> "$beforeHash&$parameter"
        }
        return joined + fragment
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() && byte >= 0 || char in "-._~:") append(char) else append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }
}
