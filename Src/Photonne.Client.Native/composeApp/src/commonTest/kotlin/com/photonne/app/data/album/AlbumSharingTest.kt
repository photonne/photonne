package com.photonne.app.data.album

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

private class SharingStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun shareLinkPayload(
    token: String = "abc123",
    hasPassword: Boolean = false,
    maxViews: Int? = null
): String {
    val maxViewsField = maxViews?.let { "\"maxViews\":$it," } ?: ""
    return """
        {
          "token": "$token",
          "albumId": "11111111-1111-1111-1111-111111111111",
          "createdAt": "2026-05-09T08:00:00Z",
          "hasPassword": $hasPassword,
          "allowDownload": true,
          $maxViewsField
          "viewCount": 0,
          "shareUrl": "https://photonne.example/share/$token"
        }
    """.trimIndent()
}

class AlbumSharingTest {

    private fun newRepository(engine: MockEngine): AlbumsRepository =
        AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = SharingStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

    @Test
    fun list_shares_passes_album_id_query() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("[${shareLinkPayload()}]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = newRepository(engine)

        val links = repository.listShares("11111111-1111-1111-1111-111111111111")
        assertEquals(1, links.size)
        assertEquals("abc123", links[0].token)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue(captured.single().second.contains("/api/share"))
        assertTrue(captured.single().second.contains("albumId=11111111-1111-1111-1111-111111111111"))
    }

    @Test
    fun create_share_posts_to_api_share_with_options() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(shareLinkPayload(hasPassword = true, maxViews = 10)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = newRepository(engine)

        val link = repository.createShare(
            albumId = "11111111-1111-1111-1111-111111111111",
            expiresAt = null,
            password = "hunter2",
            allowDownload = true,
            maxViews = 10
        )
        assertTrue(link.hasPassword)
        assertEquals(10, link.maxViews)
        assertFalse(link.shareUrl.isBlank())
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/share", captured.single().second)
    }

    @Test
    fun revoke_share_calls_delete_with_token() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repository = newRepository(engine)

        repository.revokeShare("abc123")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/share/abc123", captured.single().second)
    }

    @Test
    fun leave_album_posts_to_leave_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repository = newRepository(engine)

        repository.leave("11111111-1111-1111-1111-111111111111")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals(
            "/api/albums/11111111-1111-1111-1111-111111111111/leave",
            captured.single().second
        )
    }
}
