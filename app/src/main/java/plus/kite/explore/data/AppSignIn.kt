package plus.kite.explore.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.Serializable
import plus.kite.explore.R

/**
 * Signing in with the identity provider a server offers, brokered by the server
 * (docs/design/accounts.md section 1.1 in the explore repo). The app opens the server's start
 * page in a browser tab with a PKCE challenge, the server sends a one-time code back to the
 * app's callback, and the app trades the code and its verifier for a session token. The app
 * never holds the provider's tokens.
 */
object AppSignIn {
    /** The server the app can claim the https callback of with App Links. */
    const val CLAIMED_SERVER = "https://explore.kite.plus"
    const val HTTPS_CALLBACK = "$CLAIMED_SERVER/app/auth/done"
    const val SCHEME_CALLBACK = "explore://app/auth/done"

    /** The server keeps a sign-in open for 30 minutes; a little longer covers clock skew. */
    const val PENDING_MILLIS = 35 * 60 * 1000L

    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** A PKCE verifier: 32 random bytes in base64url, 43 characters (RFC 7636 section 4.1). */
    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        base64Url.encodeToString(ByteArray(32).also(random::nextBytes))

    /** The S256 challenge for a verifier: base64url(SHA-256(verifier)). */
    fun challenge(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /**
     * Whether the code comes back to the https callback. The app claims that address on
     * explore.kite.plus only; any other server sends it to the explore scheme, which that
     * server must allow.
     */
    fun usesHttpsCallback(server: String): Boolean = server.trimEnd('/') == CLAIMED_SERVER

    /** The page that starts signing in; switchAccount asks the provider to offer another account. */
    fun startUrl(server: String, challenge: String, switchAccount: Boolean): String {
        val query = mutableListOf("challenge" to challenge, "client" to ApiClient.CLIENT)
        if (switchAccount) query += "switch" to "1"
        if (!usesHttpsCallback(server)) query += "callback" to "scheme"
        return server.trimEnd('/') + "/auth/app/start?" +
            query.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    /** What came back to a callback address: a code, or why signing in failed. */
    sealed interface Callback {
        /** Whether it came to the https callback rather than the explore scheme. */
        val https: Boolean

        data class Code(val code: String, override val https: Boolean) : Callback
        data class Failure(val reason: String, override val https: Boolean) : Callback
    }

    /** Reads a callback address, or returns null for any other address. */
    fun parseCallback(address: String): Callback? {
        val uri = try {
            URI(address)
        } catch (_: Exception) {
            return null
        }
        val https = when {
            uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("explore.kite.plus", ignoreCase = true) &&
                uri.port == -1 && uri.rawPath == "/app/auth/done" -> true
            uri.scheme.equals("explore", ignoreCase = true) && uri.host.equals("app", ignoreCase = true) &&
                uri.rawPath == "/auth/done" -> false
            else -> return null
        }
        val query = (uri.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }
        val code = query["code"]
        if (!code.isNullOrEmpty()) return Callback.Code(code, https)
        return Callback.Failure(query["error"]?.takeIf { it.isNotEmpty() } ?: "login_failed", https)
    }

    /**
     * The message for a reason signing in failed: the server's error codes on the callback,
     * and invalid_grant from the exchange, which means the code expired or was used.
     */
    fun failureMessage(reason: String): Int = when (reason) {
        "login_expired", "invalid_grant" -> R.string.kite_plus_error_expired
        "access_denied" -> R.string.kite_plus_error_cancelled
        "unavailable" -> R.string.kite_plus_error_unavailable
        "account_disabled" -> R.string.kite_plus_error_disabled
        "email_in_use" -> R.string.kite_plus_error_email_in_use
        "email_unverified" -> R.string.kite_plus_error_email_unverified
        "registration_closed" -> R.string.sign_in_error_closed
        else -> R.string.kite_plus_error_failed
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (_: Exception) {
        text
    }
}

/** A sign-in the app started and is waiting on, kept until its callback comes or it expires. */
@Serializable
data class PendingSignIn(val server: String, val verifier: String, val https: Boolean, val startedAt: Long)

/**
 * The sign-in the app is waiting on. It is stored, encrypted, so a callback that arrives
 * after the system ended the app still finds its verifier. A callback counts only while a
 * sign-in is pending, and only once: the PKCE verifier stops a code someone else obtained,
 * and this stops callbacks nobody asked for.
 */
class PendingSignIns(private val storage: Storage, private val now: () -> Long = System::currentTimeMillis) {
    interface Storage {
        fun read(): String?
        fun write(value: String?)
    }

    /** What to do with a callback. */
    sealed interface Outcome {
        data class Exchange(val server: String, val code: String, val verifier: String) : Outcome
        data class Failed(val reason: String) : Outcome

        /** No sign-in is waiting for this callback, so it is dropped. */
        data object Ignored : Outcome
    }

    /** Starts a sign-in on a server, replacing any pending one, and returns the page to open. */
    fun start(server: String, switchAccount: Boolean, random: SecureRandom = SecureRandom()): String {
        val verifier = AppSignIn.newVerifier(random)
        val pending = PendingSignIn(server.trimEnd('/'), verifier, AppSignIn.usesHttpsCallback(server), now())
        storage.write(ExploreJson.encodeToString(PendingSignIn.serializer(), pending))
        return AppSignIn.startUrl(server, AppSignIn.challenge(verifier), switchAccount)
    }

    val pending: PendingSignIn?
        get() = storage.read()?.let {
            try {
                ExploreJson.decodeFromString(PendingSignIn.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }

    fun cancel() = storage.write(null)

    /**
     * Decides on a callback for the app's current server. A callback of the other kind than
     * the pending sign-in expects leaves it waiting; any other ends it.
     */
    fun resolve(callback: AppSignIn.Callback, server: String): Outcome {
        val pending = pending ?: return Outcome.Ignored
        if (pending.verifier.isEmpty() || callback.https != pending.https) return Outcome.Ignored
        cancel()
        val age = now() - pending.startedAt
        if (age !in 0..AppSignIn.PENDING_MILLIS || pending.server != server.trimEnd('/')) return Outcome.Failed("login_expired")
        return when (callback) {
            is AppSignIn.Callback.Code -> Outcome.Exchange(pending.server, callback.code, pending.verifier)
            is AppSignIn.Callback.Failure -> Outcome.Failed(callback.reason)
        }
    }
}
