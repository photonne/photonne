package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class StubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun jsonResponse(body: String) = ByteReadChannel(body)

class TimelineRepositoryTest {

    private val pageOne = """
        {
          "items": [
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "fileName": "IMG_001.jpg",
              "fullPath": "/photos/IMG_001.jpg",
              "fileSize": 1234,
              "fileCreatedAt": "2026-04-30T10:00:00Z",
              "fileModifiedAt": "2026-04-30T10:00:00Z",
              "extension": ".jpg",
              "scannedAt": "2026-05-01T00:00:00",
              "type": "IMAGE",
              "checksum": "abc",
              "hasExif": true,
              "hasThumbnails": true,
              "syncStatus": 0,
              "tags": ["beach"],
              "isFavorite": false,
              "isArchived": false,
              "isFileMissing": false,
              "dominantColor": "#aabbcc",
              "isReadOnly": false,
              "width": 4032,
              "height": 3024
            }
          ],
          "hasMore": true,
          "nextCursor": "2026-04-30T10:00:00Z"
        }
    """.trimIndent()

    private val pageTwo = """
        {
          "items": [
            {
              "id": "22222222-2222-2222-2222-222222222222",
              "fileName": "IMG_002.jpg",
              "fullPath": "/photos/IMG_002.jpg",
              "fileSize": 2345,
              "fileCreatedAt": "2026-04-29T10:00:00Z",
              "fileModifiedAt": "2026-04-29T10:00:00Z",
              "extension": ".jpg",
              "scannedAt": "2026-05-01T00:00:00Z",
              "type": "VIDEO",
              "hasThumbnails": false,
              "isFavorite": false,
              "isArchived": false,
              "isFileMissing": false,
              "isReadOnly": false
            }
          ],
          "hasMore": false,
          "nextCursor": null
        }
    """.trimIndent()

    @Test
    fun parses_first_page_and_forwards_cursor_to_second() = runTest {
        val captured = mutableListOf<String>()
        val engine = MockEngine { request ->
            captured += request.url.toString()
            val cursor = request.url.parameters["cursor"]
            val body = if (cursor == null) pageOne else pageTwo
            respond(
                content = jsonResponse(body),
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
        val repository = TimelineRepository(
            api = PhotonneApiClient(client, "http://test.local"),
            pageSize = 80
        )

        val first = repository.loadPage(cursor = null)
        assertEquals(1, first.items.size)
        assertEquals("IMG_001.jpg", first.items.first().fileName)
        assertTrue(first.hasMore)
        assertTrue(first.nextCursor != null)

        val second = repository.loadPage(cursor = first.nextCursor)
        assertEquals(1, second.items.size)
        assertTrue(second.items.first().isVideo)
        assertFalse(second.hasMore)
        assertNull(second.nextCursor)

        assertTrue(captured[0].contains("pageSize=80") && !captured[0].contains("cursor="))
        assertTrue(captured[1].contains("cursor="))
    }

    @Test
    fun tolerates_timestamps_without_zulu_suffix() = runTest {
        val engine = MockEngine {
            respond(
                content = jsonResponse(pageOne),
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
        val repository = TimelineRepository(
            api = PhotonneApiClient(client, "http://test.local")
        )
        val page = repository.loadPage(cursor = null)
        assertEquals(1, page.items.size)
    }
}
