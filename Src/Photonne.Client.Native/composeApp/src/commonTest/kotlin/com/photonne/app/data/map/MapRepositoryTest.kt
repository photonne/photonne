package com.photonne.app.data.map

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
import kotlin.test.assertTrue

private class MapStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class MapRepositoryTest {

    private fun newRepo(engine: MockEngine) = MapRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = MapStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun points_calls_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("[]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val points = repo.points()
        assertTrue(points.isEmpty())
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/assets/map/points", captured.single().second)
    }

    @Test
    fun points_decodes_response_payload() = runTest {
        val payload = """
            [
              {
                "id": "p1",
                "latitude": 41.4,
                "longitude": 2.17,
                "hasThumbnail": true,
                "date": "2026-01-01T00:00:00Z"
              },
              {
                "id": "p2",
                "latitude": 40.4,
                "longitude": -3.7,
                "hasThumbnail": false,
                "date": "2026-02-01T00:00:00Z"
              }
            ]
        """.trimIndent()
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val points = repo.points()
        assertEquals(2, points.size)
        assertEquals("p1", points[0].id)
        assertTrue(points[0].hasThumbnail)
        assertEquals("p2", points[1].id)
        assertEquals(false, points[1].hasThumbnail)
    }
}
