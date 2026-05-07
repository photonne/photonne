package com.photonne.native.data.api

import com.photonne.native.data.auth.AuthState
import com.photonne.native.data.auth.AuthStateHolder
import com.photonne.native.data.auth.TokenStorage
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
    fun marks_unauthenticated_when_refresh_fails() = runTest {
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
}
