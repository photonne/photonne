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
    fun clusters_forwards_zoom_and_bounds_query_params() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("[]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.clusters(
            zoom = 7,
            minLat = 40.0,
            minLng = -3.5,
            maxLat = 41.0,
            maxLng = -2.5
        )

        val url = captured.single().second
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path") { url.contains("/api/assets/map") }
        assertTrue("zoom") { url.contains("zoom=7") }
        assertTrue("minLat") { url.contains("minLat=40") }
        assertTrue("minLng") { url.contains("minLng=-3.5") }
        assertTrue("maxLat") { url.contains("maxLat=41") }
        assertTrue("maxLng") { url.contains("maxLng=-2.5") }
    }

    @Test
    fun clusters_decodes_response_payload() = runTest {
        val payload = """
            [
              {
                "id": "c1",
                "latitude": 40.0,
                "longitude": -3.0,
                "count": 4,
                "assetIds": ["a","b","c","d"],
                "earliestDate": "2026-01-01T00:00:00Z",
                "latestDate": "2026-02-01T00:00:00Z",
                "firstAssetId": "a",
                "hasThumbnail": true
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

        val clusters = repo.clusters(zoom = 5)
        assertEquals(1, clusters.size)
        assertEquals("c1", clusters[0].id)
        assertEquals(4, clusters[0].count)
        assertEquals("a", clusters[0].firstAssetId)
        assertTrue { clusters[0].hasThumbnail }
    }
}
