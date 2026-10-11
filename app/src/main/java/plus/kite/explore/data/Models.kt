package plus.kite.explore.data

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors docs/design/api.md in the explore repository. Fields the app does
// not need are left out; decoding tolerates fields the server adds later.

@Serializable
data class BlogRef(
    val host: String,
    val name: String,
    @SerialName("site_url") val siteUrl: String = "",
    val language: String = "",
)

enum class LinkStatus { Available, Unavailable, Unknown;

    companion object {
        fun of(raw: String?): LinkStatus = when (raw) {
            "available" -> Available
            "unavailable" -> Unavailable
            else -> Unknown
        }
    }
}

@Serializable
data class Entry(
    val id: String,
    val title: String,
    val url: String,
    val excerpt: String? = null,
    /** A path on the Explore server, such as /api/v1/entries/12/image. */
    @SerialName("image_url") val imagePath: String? = null,
    @SerialName("published_at") @Serializable(Rfc3339Serializer::class) val publishedAt: Instant? = null,
    val tags: List<String> = emptyList(),
    @SerialName("link_status") val linkStatusRaw: String? = null,
    @SerialName("link_checked_at") @Serializable(Rfc3339Serializer::class) val linkCheckedAt: Instant? = null,
    val blog: BlogRef? = null,
) {
    val linkStatus: LinkStatus get() = LinkStatus.of(linkStatusRaw)
}

@Serializable
data class Blog(
    val host: String,
    val name: String,
    /** The blog's own description; empty when it has none. */
    @SerialName("description") val about: String = "",
    @SerialName("site_url") val siteUrl: String,
    @SerialName("feed_url") val feedUrl: String = "",
    val language: String = "",
    val generator: String = "unknown",
    @SerialName("last_published_at") @Serializable(Rfc3339Serializer::class) val lastPublishedAt: Instant? = null,
) {
    val ref: BlogRef get() = BlogRef(host, name, siteUrl, language)
}

@Serializable
data class Page<T>(
    val data: List<T> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class BlogPage(
    val blog: Blog,
    val entries: List<Entry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class DataList<T>(val data: List<T> = emptyList())

/** A tag from Explore's fixed list, named in Chinese and English. */
@Serializable
data class Topic(val slug: String, val name: Map<String, String> = emptyMap()) {
    fun name(language: AppLanguage): String = name[language.code] ?: name["en"] ?: slug
}

@Serializable
data class LinkState(
    @SerialName("link_status") val linkStatusRaw: String? = null,
    @SerialName("link_checked_at") @Serializable(Rfc3339Serializer::class) val linkCheckedAt: Instant? = null,
    val checking: Boolean = false,
) {
    val linkStatus: LinkStatus get() = LinkStatus.of(linkStatusRaw)
}

@Serializable
data class SiteConfig(
    val notice: String = "",
    @SerialName("registration_enabled") val registrationEnabled: Boolean = true,
)

@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("csrf_token") val csrfToken: String = "",
    /** True while the password is one an admin reset; servers before Explore 0.1.9 leave it out. */
    @SerialName("temporary_password") val temporaryPassword: Boolean = false,
    /** False for an account that signs in with Kite Plus only; servers before Kite Plus sign-in leave it out. */
    @SerialName("password_login") val passwordLogin: Boolean = true,
    /** When the account was linked to a Kite Plus account, if it was. */
    @SerialName("identity_linked_at") @Serializable(Rfc3339Serializer::class) val identityLinkedAt: Instant? = null,
)

/** A session an app signed in to: the token to send as a Bearer token and the account. */
@Serializable
data class AppSession(val token: String, val user: User)

/** How a server signs readers in (GET /api/v1/auth/options). */
@Serializable
data class AuthOptions(
    val password: Boolean = true,
    /** The identity provider the server signs in through, or null when it has none. */
    val oidc: Oidc? = null,
) {
    @Serializable
    data class Oidc(
        /** The provider's name in the interface language, such as "Kite Plus account". */
        val name: String = "",
        /** The provider's page for managing and deleting the account. */
        @SerialName("account_url") val accountUrl: String = "",
    )
}

/** What an OPML import did: the outlines read, the blogs followed, and those Explore does not list. */
@Serializable
data class OpmlImport(
    val outlines: Int = 0,
    val added: Int = 0,
    @SerialName("already_following") val alreadyFollowing: Int = 0,
    /** Outlines past the server's limit, which were not read. */
    val ignored: Int = 0,
    @SerialName("not_listed") val notListed: List<Outline> = emptyList(),
) {
    @Serializable
    data class Outline(
        val title: String = "",
        @SerialName("site_url") val siteUrl: String = "",
        @SerialName("feed_url") val feedUrl: String = "",
    )
}

@Serializable
data class ClaimChallenge(
    val record: String,
    val value: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 1800,
)

enum class Severity { Error, Warning, Info;

    companion object {
        fun of(raw: String?): Severity = when (raw) {
            "error" -> Error
            "warning" -> Warning
            else -> Info
        }
    }
}

@Serializable
data class Problem(
    val code: String,
    @SerialName("severity") val severityRaw: String? = null,
    val count: Int? = null,
    val detail: String? = null,
    val hint: String? = null,
) {
    val severity: Severity get() = Severity.of(severityRaw)
}

@Serializable
data class CheckReport(
    @SerialName("input_url") val inputUrl: String = "",
    @SerialName("feed_url") val feedUrl: String? = null,
    val format: String? = null,
    val generator: String? = null,
    val title: String? = null,
    @SerialName("latest_entry_title") val latestEntryTitle: String? = null,
    val language: String? = null,
    val problems: List<Problem> = emptyList(),
    val passed: Boolean = false,
)

enum class SubmissionStatus { Pending, Approved, Rejected;

    companion object {
        fun of(raw: String?): SubmissionStatus = when (raw) {
            "approved" -> Approved
            "rejected" -> Rejected
            else -> Pending
        }
    }
}

@Serializable
data class Submission(
    val id: String,
    @SerialName("status") val statusRaw: String? = null,
    val host: String,
    @SerialName("site_url") val siteUrl: String = "",
    @SerialName("feed_url") val feedUrl: String = "",
    @SerialName("check_report") val checkReport: CheckReport? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") @Serializable(Rfc3339Serializer::class) val createdAt: Instant? = null,
    @SerialName("reviewed_at") @Serializable(Rfc3339Serializer::class) val reviewedAt: Instant? = null,
) {
    val status: SubmissionStatus get() = SubmissionStatus.of(statusRaw)
}

@Serializable
data class SubmissionPreview(
    val host: String,
    @SerialName("site_url") val siteUrl: String,
    @SerialName("feed_url") val feedUrl: String,
    val title: String = "",
    val description: String = "",
    @SerialName("latest_entry_title") val latestEntryTitle: String? = null,
    val generator: String = "",
    val language: String = "",
    @SerialName("items_total") val itemsTotal: Int = 0,
    @SerialName("items_valid") val itemsValid: Int = 0,
    @SerialName("check_report") val checkReport: CheckReport? = null,
    val passed: Boolean = false,
)

/** The body of every error response. */
@Serializable
data class ErrorEnvelope(
    val error: Body,
    @SerialName("submission_id") val submissionId: String? = null,
    @SerialName("check_report") val checkReport: CheckReport? = null,
) {
    @Serializable
    data class Body(val code: String, val message: String = "")
}

/** A notice or ad Explore publishes itself, shown between the latest posts; see docs/design/notices.md. */
@Serializable
data class Notice(
    val id: String,
    /** "notice" or "ad". Anything but a notice shows as an ad, so a kind added later is never left unmarked. */
    val kind: String = "ad",
    val title: String,
    val summary: String = "",
    /** Where it opens; empty for one that opens its page on Explore. */
    val url: String = "",
    @SerialName("source_name") val sourceName: String = "",
    /** 0 before the first post, N after the Nth. */
    val position: Int = 0,
    @SerialName("published_at") @Serializable(Rfc3339Serializer::class) val publishedAt: Instant? = null,
    /** Plain text, only on a single notice. */
    val body: String? = null,
) {
    val isNotice: Boolean get() = kind == "notice"
    val isPinned: Boolean get() = position == 0
}
