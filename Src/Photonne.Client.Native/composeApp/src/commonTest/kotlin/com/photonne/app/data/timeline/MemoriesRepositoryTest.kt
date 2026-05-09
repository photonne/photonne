package com.photonne.app.data.timeline

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

private class MemoriesStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class MemoriesRepositoryTest {

    @Test
    fun memories_endpoint_hits_assets_memories_path() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val payload = """[
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "fileName": "memory.jpg",
              "fullPath": "/photos/memory.jpg",
              "fileSize": 1234,
              "fileCreatedAt": "2023-05-09T10:00:00Z",
              "fileModifiedAt": "2023-05-09T10:00:00Z",
              "extension": ".jpg",
              "scannedAt": "2023-05-10T00:00:00Z",
              "type": "IMAGE",
              "hasThumbnails": true
            }
        ]""".trimIndent()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = MemoriesRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = MemoriesStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

        val items = repo.list()
        assertEquals(1, items.size)
        assertEquals("memory.jpg", items[0].fileName)
        assertTrue(items[0].hasThumbnails)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/assets/memories", captured.single().second)
    }
}
