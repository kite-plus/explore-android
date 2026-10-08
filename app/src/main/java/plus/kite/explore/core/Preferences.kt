package plus.kite.explore.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The reader's settings, kept on the device and observed by the screens that show them. */
class Preferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("explore", Context.MODE_PRIVATE)

    /** Open posts in the default browser instead of a tab inside the app. */
    var openInBrowser by BooleanPref(prefs, "links.openInBrowser", false)

    /** Whether a post opens by way of the transition page: on until the reader turns it off. */
    var showsHandoff by BooleanPref(prefs, "links.handoff", true)

    /** Whether posts the reader opened are remembered and dimmed: on until the reader turns it off. */
    var dimsReadPosts by BooleanPref(prefs, "reading.dimsRead", true)

    var discoverLanguage by StringPref(prefs, "discover.language", "all")
    var blogsLanguage by StringPref(prefs, "blogs.language", "all")
    var topicLanguage by StringPref(prefs, "topic.language", "all")

    /** The site notice the reader closed, so it stays closed until it changes. */
    var dismissedNotice by StringPref(prefs, "notice.dismissed", "")

    var server: String?
        get() = prefs.getString("server", null)
        set(value) = prefs.edit { if (value == null) remove("server") else putString("server", value) }

    fun submissions(server: String): List<String> =
        prefs.getString("submissions:$server", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun setSubmissions(server: String, ids: List<String>) {
        prefs.edit { putString("submissions:$server", ids.take(50).joinToString("\n")) }
    }

    /** Addresses of opened posts, oldest first. */
    var readPosts: List<String>
        get() = prefs.getString("read.posts", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        set(value) = prefs.edit { if (value.isEmpty()) remove("read.posts") else putString("read.posts", value.joinToString("\n")) }

    private class BooleanPref(private val prefs: SharedPreferences, private val key: String, default: Boolean) {
        private var state by mutableStateOf(prefs.getBoolean(key, default))

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Boolean = state

        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            state = value
            prefs.edit { putBoolean(key, value) }
        }
    }

    private class StringPref(private val prefs: SharedPreferences, private val key: String, default: String) {
        private var state by mutableStateOf(prefs.getString(key, default) ?: default)

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String = state

        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) {
            state = value
            prefs.edit { putString(key, value) }
        }
    }
}

/**
 * Session tokens per server, encrypted with a key that never leaves the Android Keystore.
 * A token that cannot be decrypted, such as one restored onto a new phone, reads as signed out.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("explore.tokens", Context.MODE_PRIVATE)

    fun token(server: String): String? {
        val stored = prefs.getString(server, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val ivLength = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)).decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    fun setToken(server: String, token: String?) {
        if (token == null) {
            prefs.edit { remove(server) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(token.encodeToByteArray())
        val bytes = byteArrayOf(iv.size.toByte()) + iv + sealed
        prefs.edit { putString(server, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "plus.kite.explore.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
