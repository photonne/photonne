package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class FavoriteToggleTest {

    @Test
    fun toggle_favorite_posts_and_returns_new_state() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val responses = ArrayDeque(listOf(true, false))
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            val isFavorite = responses.removeFirst()
            respond(
                content = ByteReadChannel("""{"isFavorite":$isFavorite}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = StubTokenStorage(),
            authState = AuthStateHolder()
        )
        val repository = AssetDetailRepository(api = PhotonneApiClient(client, "http://test.local"))

        val first = repository.toggleFavorite("aaa")
        val second = repository.toggleFavorite("aaa")

        assertTrue(first)
        assertFalse(second)
        assertEquals(2, captured.size)
        captured.forEach { (method, path) ->
            assertEquals(HttpMethod.Post, method)
            assertEquals("/api/assets/aaa/favorite", path)
        }
    }
}
