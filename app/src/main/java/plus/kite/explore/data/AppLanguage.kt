package plus.kite.explore.data

import java.util.Locale

/** The interface language. The app ships in Chinese and English; posts always keep their own language. */
enum class AppLanguage(val code: String) {
    Zh("zh"), En("en");

    /** Sent as Accept-Language so error messages and check hints match the interface. */
    val acceptLanguage: String get() = if (this == Zh) "zh-CN" else "en"

    /** Explore's web pages for this language live under this path prefix. */
    val webPrefix: String get() = if (this == Zh) "" else "/en"

    companion object {
        val current: AppLanguage
            get() = if (Locale.getDefault().language == "zh") Zh else En
    }
}
