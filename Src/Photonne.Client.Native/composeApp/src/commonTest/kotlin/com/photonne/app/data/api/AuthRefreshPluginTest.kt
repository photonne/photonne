package com.photonne.app.data.api

import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTokenStorage(
    private var access: String? = "old-access",
    private var refresh: String? = "refresh-1",
    private val deviceId: String = "device-1"
) : TokenStorage {
    val saved = mutableListOf<Pair<String, String>>()
    var clearedTimes = 0
    override fun getAccessToken() = access
    override fun getRefreshToken() = refresh
    override fun getDeviceId() = deviceId
    override fun saveTokens(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
        saved += accessToken to refreshToken
    }
    override fun clear() {
        access = null
        refresh = null
        clearedTimes++
    }
}

class AuthRefreshPluginTest {

    @Test
    fun retries_after_401_with_refreshed_token() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()
        val calls = mutableListOf<String>()

        val engine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            calls += "${request.method.value} ${request.url.encodedPath} auth=$auth"
            when {
                request.url.encodedPath == "/api/protected" && auth == "Bearer old-access" ->
                    respond("", HttpStatusCode.Unauthorized)
                request.url.encodedPath == "/api/auth/refresh" ->
                    respond(
                        content = ByteReadChannel("""{"token":"new-access","refreshToken":"refresh-2"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                request.url.encodedPath == "/api/protected" && auth == "Bearer new-access" ->
                    respond("ok", HttpStatusCode.OK)
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )

        val response: HttpResponse = client.get("http://test.local/api/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
        assertEquals("new-access" to "refresh-2", storage.saved.last())
        assertTrue(calls.any { it.contains("/api/auth/refresh") })
        assertEquals(AuthState.Unknown, authState.state.value)
    }

    @Test
    fun marks_unauthenticated_when_refresh_rejected() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/protected" -> respond("", HttpStatusCode.Unauthorized)
                "/api/auth/refresh" -> respond("", HttpStatusCode.Unauthorized)
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )

        val response: HttpResponse = client.get("http://test.local/api/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthState.Unauthenticated, authState.state.value)
        assertEquals(1, storage.clearedTimes)
    }

    @Test
    fun logs_out_when_refresh_returns_403() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/protected" -> respond("", HttpStatusCode.Unauthorized)
                "/api/auth/refresh" -> respond("", HttpStatusCode.Forbidden)
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )

        val response: HttpResponse = client.get("http://test.local/api/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthState.Unauthenticated, authState.state.value)
        assertEquals(1, storage.clearedTimes)
    }

    @Test
    fun preserves_session_when_refresh_throws() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/protected" -> respond("", HttpStatusCode.Unauthorized)
                // Simulate a transient network failure during refresh.
                "/api/auth/refresh" -> throw RuntimeException("connect timeout")
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )

        val response: HttpResponse = client.get("http://test.local/api/protected")
        // Original 401 surfaces, but the session is left intact for a retry.
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthState.Unknown, authState.state.value)
        assertEquals(0, storage.clearedTimes)
    }

    @Test
    fun preserves_session_when_refresh_returns_5xx() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/protected" -> respond("", HttpStatusCode.Unauthorized)
                "/api/auth/refresh" -> respond("", HttpStatusCode.InternalServerError)
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )

        val response: HttpResponse = client.get("http://test.local/api/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthState.Unknown, authState.state.value)
        assertEquals(0, storage.clearedTimes)
    }

    @Test
    fun fires_connection_error_callback_when_request_throws() = runTest {
        val storage = FakeTokenStorage()
        val authState = AuthStateHolder()
        var connectionErrors = 0

        val engine = MockEngine { throw RuntimeException("connect timeout") }

        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState,
            onConnectionError = { connectionErrors++ }
        )

        var thrown = false
        try {
            client.get("http://test.local/api/protected")
        } catch (_: Throwable) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(1, connectionErrors)
        // A connection failure is not an auth failure — session untouched.
        assertEquals(0, storage.clearedTimes)
        assertEquals(AuthState.Unknown, authState.state.value)
    }
}
