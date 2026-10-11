package plus.kite.explore

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.AppSignIn
import plus.kite.explore.data.AuthOptions
import plus.kite.explore.data.PendingSignIns
import plus.kite.explore.data.ExploreJson
import plus.kite.explore.data.User

private const val TOKEN = "Pz6bJ0b0Hk3A1wq0cX0p5mXx2Zt5hYQm8fE4dS1aB2c"

private const val USER = """{"id":"u1","email":"alice@example.com","display_name":"Alice","csrf_token":"csrf1",""" +
    """"password_login":false,"identity_linked_at":"2026-10-10T08:00:00Z"}"""

class SessionTest {
    @Test
    fun tokenFromTheBody() {
        val (user, token) = ApiClient.session("""{"token":"$TOKEN","expires_at":"2026-11-10T08:00:00Z","user":$USER}""", emptyMap())
        assertEquals(TOKEN, token)
        assertEquals("Alice", user.displayName)
        assertFalse(user.passwordLogin)
    }

    @Test
    fun tokenFromTheCookieOfAnOlderServer() {
        val headers = mapOf<String?, List<String>>("Set-Cookie" to listOf("explore_session=$TOKEN; Path=/; HttpOnly"))
        val (user, token) = ApiClient.session("""{"id":"u1","email":"a@example.com","display_name":"A","csrf_token":"c"}""", headers)
        assertEquals(TOKEN, token)
        assertEquals("c", user.csrfToken)
    }

    @Test
    fun noTokenAnywhereIsAnInvalidResponse() {
        for (body in listOf("""{"id":"u1","email":"a@example.com","display_name":"A"}""", """{"token":"","user":$USER}""", "<html>", "[]")) {
            try {
                ApiClient.session(body, emptyMap())
                fail(body)
            } catch (_: ApiError.InvalidResponse) {
            }
        }
    }

    @Test
    fun linkedAccount() {
        val user = ExploreJson.decodeFromString(User.serializer(), USER)
        assertEquals(java.time.Instant.parse("2026-10-10T08:00:00Z"), user.identityLinkedAt)
        val older = ExploreJson.decodeFromString(User.serializer(), """{"id":"u1","email":"a@example.com","display_name":"A","identity_linked_at":null}""")
        assertTrue(older.passwordLogin)
        assertNull(older.identityLinkedAt)
    }

    @Test
    fun bearerAndCookieTogether() {
        assertEquals(
            listOf("Authorization" to "Bearer $TOKEN", "Cookie" to "explore_session=$TOKEN"),
            ApiClient.sessionHeaders(TOKEN, "csrf1", "GET"),
        )
        assertEquals(
            listOf("Authorization" to "Bearer $TOKEN", "Cookie" to "explore_session=$TOKEN", "X-CSRF-Token" to "csrf1"),
            ApiClient.sessionHeaders(TOKEN, "csrf1", "PUT"),
        )
        assertEquals(2, ApiClient.sessionHeaders(TOKEN, "", "DELETE").size)
    }
}

/** The client against a server on this machine, so the headers and bodies are the ones really sent. */
class ApiClientWireTest {
    @Test
    fun passwordSignInAsksForAToken() = runBlocking {
        FakeServer { FakeServer.Response(200, """{"token":"$TOKEN","expires_at":"2026-11-10T08:00:00Z","user":$USER}""") }.use { server ->
            val (_, token) = ApiClient(server.url).signIn("alice@example.com", "secret")
            assertEquals(TOKEN, token)
            val request = server.requests.single()
            assertEquals("/api/v1/auth/login", request.path)
            val body = Json.parseToJsonElement(request.body).jsonObject
            assertEquals("android", body["client"]?.jsonPrimitive?.content)
            assertEquals("alice@example.com", body["email"]?.jsonPrimitive?.content)
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
        }
    }

    @Test
    fun passwordSignInOnAnOlderServerReadsTheCookie() = runBlocking {
        FakeServer {
            FakeServer.Response(200, USER, listOf("Set-Cookie" to "explore_session=$TOKEN; Path=/; HttpOnly; SameSite=Lax"))
        }.use { server ->
            assertEquals(TOKEN, ApiClient(server.url).signIn("alice@example.com", "secret").second)
        }
    }

    @Test
    fun badCredentials() = runBlocking {
        FakeServer { FakeServer.Response(401, """{"error":{"code":"bad_credentials","message":"Wrong email or password."}}""") }.use { server ->
            try {
                ApiClient(server.url).signIn("alice@example.com", "wrong")
                fail()
            } catch (error: ApiError) {
                assertEquals("bad_credentials", error.code)
            }
        }
    }

    @Test
    fun signedRequestsSendBothBearerAndCookie() = runBlocking {
        FakeServer { request ->
            if (request.method == "GET") FakeServer.Response(200, USER) else FakeServer.Response(204)
        }.use { server ->
            val client = ApiClient(server.url, TOKEN).apply { csrfToken = "csrf1" }
            client.me()
            client.subscribe("blog.example.com")
            val (read, write) = server.requests
            assertEquals("Bearer $TOKEN", read.header("Authorization"))
            assertEquals("explore_session=$TOKEN", read.header("Cookie"))
            assertNull(read.header("X-CSRF-Token"))
            assertEquals("PUT", write.method)
            assertEquals("Bearer $TOKEN", write.header("Authorization"))
            assertEquals("explore_session=$TOKEN", write.header("Cookie"))
            assertEquals("csrf1", write.header("X-CSRF-Token"))
        }
    }

    @Test
    fun anonymousRequestsSendNoCredentials() = runBlocking {
        FakeServer { FakeServer.Response(200, """{"notice":"","registration_enabled":true}""") }.use { server ->
            ApiClient(server.url, TOKEN).siteConfig()
            val request = server.requests.single()
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
        }
    }
}

class PkceTest {
    @Test
    fun challengeMatchesRfc7636() {
        // RFC 7636 appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", AppSignIn.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun verifiersAreFreshAndWellFormed() {
        val verifiers = (1..50).map { AppSignIn.newVerifier() }
        assertEquals(50, verifiers.toSet().size)
        for (verifier in verifiers) {
            assertEquals(43, verifier.length)
            assertTrue(verifier, verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
            assertEquals(43, AppSignIn.challenge(verifier).length)
        }
    }
}

class StartUrlTest {
    private val challenge = AppSignIn.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

    @Test
    fun explorePlusUsesTheHttpsCallback() {
        assertEquals(
            "https://explore.kite.plus/auth/app/start?challenge=$challenge&client=android",
            AppSignIn.startUrl("https://explore.kite.plus/", challenge, switchAccount = false),
        )
        assertTrue(AppSignIn.usesHttpsCallback("https://explore.kite.plus"))
    }

    @Test
    fun otherServersUseTheScheme() {
        assertEquals(
            "http://localhost:8080/auth/app/start?challenge=$challenge&client=android&switch=1&callback=scheme",
            AppSignIn.startUrl("http://localhost:8080", challenge, switchAccount = true),
        )
        for (server in listOf("https://explore.example.com", "http://explore.kite.plus", "https://explore.kite.plus:8443")) {
            assertFalse(server, AppSignIn.usesHttpsCallback(server))
        }
    }
}

class CallbackTest {
    @Test
    fun readsCodes() {
        assertEquals(AppSignIn.Callback.Code("abc-_1", true), AppSignIn.parseCallback("https://explore.kite.plus/app/auth/done?code=abc-_1"))
        assertEquals(AppSignIn.Callback.Code("abc", false), AppSignIn.parseCallback("explore://app/auth/done?code=abc"))
    }

    @Test
    fun readsFailures() {
        assertEquals(AppSignIn.Callback.Failure("email_in_use", false), AppSignIn.parseCallback("explore://app/auth/done?error=email_in_use"))
        assertEquals(AppSignIn.Callback.Failure("login_failed", true), AppSignIn.parseCallback("https://explore.kite.plus/app/auth/done"))
        assertEquals(AppSignIn.Callback.Failure("login_failed", false), AppSignIn.parseCallback("explore://app/auth/done?code="))
    }

    @Test
    fun leavesOtherAddressesAlone() {
        val others = listOf(
            "explore://blogs/example.com",
            "explore://app/other",
            "https://explore.kite.plus/blogs/example.com",
            "https://evil.example/app/auth/done?code=abc",
            "http://explore.kite.plus/app/auth/done?code=abc",
            "https://explore.kite.plus:444/app/auth/done?code=abc",
            "not a url",
        )
        for (address in others) assertNull(address, AppSignIn.parseCallback(address))
    }

    @Test
    fun mapsEveryFailureToItsMessage() {
        val expected = mapOf(
            "login_expired" to R.string.kite_plus_error_expired,
            "invalid_grant" to R.string.kite_plus_error_expired,
            "login_failed" to R.string.kite_plus_error_failed,
            "unavailable" to R.string.kite_plus_error_unavailable,
            "account_disabled" to R.string.kite_plus_error_disabled,
            "email_in_use" to R.string.kite_plus_error_email_in_use,
            "email_unverified" to R.string.kite_plus_error_email_unverified,
            "registration_closed" to R.string.sign_in_error_closed,
            "something_new" to R.string.kite_plus_error_failed,
        )
        for ((reason, message) in expected) assertEquals(reason, message, AppSignIn.failureMessage(reason))
        // Cancelling, at the provider or on the server's confirmation page, says nothing.
        assertNull(AppSignIn.failureMessage("access_denied"))
    }
}

class PendingSignInTest {
    private class Memory : PendingSignIns.Storage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) {
            this.value = value
        }
    }

    private var clock = 1_000_000L
    private val storage = Memory()
    private val signIns = PendingSignIns(storage) { clock }
    private val local = "http://localhost:8080"

    @Test
    fun startsWithAChallengeForTheStoredVerifier() {
        val url = signIns.start("$local/", switchAccount = false)
        val pending = signIns.pending!!
        assertEquals(local, pending.server)
        assertFalse(pending.https)
        assertTrue(url, url.contains("challenge=" + AppSignIn.challenge(pending.verifier)))
        assertTrue(storage.value!!.contains(pending.verifier))
    }

    @Test
    fun exchangesACodeOnce() {
        signIns.start(local, switchAccount = false)
        val verifier = signIns.pending!!.verifier
        val callback = AppSignIn.Callback.Code("code1", https = false)
        assertEquals(PendingSignIns.Outcome.Exchange(local, "code1", verifier), signIns.resolve(callback, local))
        assertNull(storage.value)
        assertEquals(PendingSignIns.Outcome.Ignored, signIns.resolve(callback, local))
    }

    @Test
    fun ignoresCallbacksNobodyAskedFor() {
        assertEquals(PendingSignIns.Outcome.Ignored, signIns.resolve(AppSignIn.Callback.Code("code1", https = false), local))
    }

    @Test
    fun keepsWaitingThroughTheOtherKindOfCallback() {
        signIns.start(AppSignIn.CLAIMED_SERVER, switchAccount = false)
        assertEquals(PendingSignIns.Outcome.Ignored, signIns.resolve(AppSignIn.Callback.Code("planted", https = false), AppSignIn.CLAIMED_SERVER))
        val outcome = signIns.resolve(AppSignIn.Callback.Code("real", https = true), AppSignIn.CLAIMED_SERVER)
        assertEquals("real", (outcome as PendingSignIns.Outcome.Exchange).code)
    }

    @Test
    fun passesFailuresOn() {
        signIns.start(local, switchAccount = true)
        assertEquals(PendingSignIns.Outcome.Failed("access_denied"), signIns.resolve(AppSignIn.Callback.Failure("access_denied", false), local))
        assertNull(signIns.pending)
    }

    @Test
    fun expires() {
        signIns.start(local, switchAccount = false)
        clock += AppSignIn.PENDING_MILLIS + 1
        assertEquals(PendingSignIns.Outcome.Failed("login_expired"), signIns.resolve(AppSignIn.Callback.Code("late", false), local))
        assertNull(signIns.pending)
    }

    @Test
    fun belongsToTheServerItStartedOn() {
        signIns.start(local, switchAccount = false)
        assertEquals(PendingSignIns.Outcome.Failed("login_expired"), signIns.resolve(AppSignIn.Callback.Code("c", false), "http://127.0.0.1:8080"))
    }

    @Test
    fun unreadableStateCountsAsNone() {
        storage.value = "garbage"
        assertNull(signIns.pending)
        assertEquals(PendingSignIns.Outcome.Ignored, signIns.resolve(AppSignIn.Callback.Code("c", false), local))
    }

    @Test
    fun cancels() {
        signIns.start(local, switchAccount = false)
        signIns.cancel()
        assertNull(signIns.pending)
    }
}

class AuthOptionsTest {
    @Test
    fun decodesKitePlus() {
        val options = ExploreJson.decodeFromString(
            AuthOptions.serializer(),
            """{"password":true,"oidc":{"name":"Kite Plus 账号","account_url":"https://id.kite.plus/ui/console/users/me"}}""",
        )
        assertTrue(options.password)
        assertEquals("Kite Plus 账号", options.oidc?.name)
        assertEquals("https://id.kite.plus/ui/console/users/me", options.oidc?.accountUrl)
    }

    @Test
    fun decodesPasswordOnly() {
        assertNull(ExploreJson.decodeFromString(AuthOptions.serializer(), """{"password":true,"oidc":null}""").oidc)
    }

    @Test
    fun sendsTheLanguageAndReadsTheAnswer() = runBlocking {
        FakeServer { FakeServer.Response(200, """{"password":false,"oidc":{"name":"Kite Plus account","account_url":"https://id.example/me"}}""") }.use { server ->
            val options = ApiClient(server.url).authOptions()
            assertFalse(options.password)
            assertEquals("Kite Plus account", options.oidc?.name)
            val request = server.requests.single()
            assertEquals("/api/v1/auth/options", request.path)
            assertTrue(request.header("Accept-Language") in setOf("en", "zh-CN"))
        }
    }

    @Test
    fun anOlderServerHasPasswordsOnly() = runBlocking {
        FakeServer { FakeServer.Response(404, """{"error":{"code":"not_found","message":"Not found."}}""") }.use { server ->
            assertEquals(AuthOptions(password = true, oidc = null), ApiClient(server.url).authOptions())
        }
    }

    @Test
    fun otherFailuresAreErrors() = runBlocking {
        FakeServer { FakeServer.Response(502, "<html>Bad gateway</html>", listOf("Content-Type" to "text/html")) }.use { server ->
            try {
                ApiClient(server.url).authOptions()
                fail()
            } catch (error: ApiError) {
                assertEquals(502, error.status)
            }
        }
    }
}

class ExchangeTest {
    @Test
    fun tradesTheCodeAndVerifierForAToken() = runBlocking {
        FakeServer { FakeServer.Response(200, """{"token":"$TOKEN","expires_at":"2026-11-10T08:00:00Z","user":$USER}""") }.use { server ->
            val (user, token) = ApiClient(server.url).exchange("code1", "verifier1")
            assertEquals(TOKEN, token)
            assertEquals("alice@example.com", user.email)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/auth/app/exchange", request.path)
            assertTrue(request.header("Content-Type")!!.startsWith("application/json"))
            val body = Json.parseToJsonElement(request.body).jsonObject
            assertEquals("code1", body["code"]?.jsonPrimitive?.content)
            assertEquals("verifier1", body["verifier"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun aBadCodeIsInvalidGrant() = runBlocking {
        FakeServer { FakeServer.Response(400, """{"error":{"code":"invalid_grant","message":"x"}}""") }.use { server ->
            try {
                ApiClient(server.url).exchange("code1", "verifier1")
                fail()
            } catch (error: ApiError) {
                assertEquals("invalid_grant", error.code)
                assertEquals(R.string.kite_plus_error_expired, AppSignIn.failureMessage(error.code!!))
            }
        }
    }
}
