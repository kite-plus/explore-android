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
