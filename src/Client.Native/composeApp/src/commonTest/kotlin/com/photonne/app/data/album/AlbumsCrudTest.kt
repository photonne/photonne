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
import kotlin.test.assertTrue

private class AlbumsStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun newAlbumPayload(id: String, name: String, description: String? = null): String {
    val desc = description?.let { "\"$it\"" } ?: "null"
    return """
        {
          "id": "$id",
          "name": "$name",
          "description": $desc,
          "createdAt": "2026-05-09T08:00:00Z",
          "updatedAt": "2026-05-09T08:00:00Z",
          "assetCount": 0,
          "previewThumbnailUrls": [],
          "isOwner": true,
          "isShared": false,
          "sharedWithCount": 0,
          "canRead": true,
          "canWrite": true,
          "canDelete": true,
          "canManagePermissions": true,
          "hasActiveShareLink": false
        }
    """.trimIndent()
}

class AlbumsCrudTest {

    @Test
    fun create_posts_to_albums_and_returns_summary() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(newAlbumPayload("11111111-1111-1111-1111-111111111111", "Holidays")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = AlbumsStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

        val album = repository.create(name = "Holidays", description = "Summer 2026")
        assertEquals("Holidays", album.name)
        assertEquals(1, captured.size)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/albums", captured.single().second)
    }

    @Test
    fun update_uses_put_with_album_id() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(newAlbumPayload("aaa", "Renamed")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = AlbumsStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

        val album = repository.update("aaa", "Renamed", null)
        assertEquals("Renamed", album.name)
        assertEquals(HttpMethod.Put, captured.single().first)
        assertEquals("/api/albums/aaa", captured.single().second)
    }

    @Test
    fun delete_returns_unit_on_success() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent
            )
        }
        val repository = AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = AlbumsStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

        repository.delete("aaa")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/albums/aaa", captured.single().second)
    }

    @Test
    fun add_asset_posts_to_album_assets_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent
            )
        }
        val repository = AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = AlbumsStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

        repository.addAsset(albumId = "alb-1", assetId = "ast-1")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertTrue(captured.single().second.endsWith("/api/albums/alb-1/assets"))
    }
}
