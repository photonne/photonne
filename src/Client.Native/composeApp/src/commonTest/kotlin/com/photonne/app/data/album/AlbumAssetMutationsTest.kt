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

private class MutationStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun albumPayload(coverUrl: String?): String {
    val cover = coverUrl?.let { "\"$it\"" } ?: "null"
    return """
        {
          "id": "alb-1",
          "name": "Holidays",
          "description": null,
          "createdAt": "2026-05-09T08:00:00Z",
          "updatedAt": "2026-05-09T08:00:00Z",
          "assetCount": 5,
          "coverThumbnailUrl": $cover,
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

class AlbumAssetMutationsTest {

    private fun newRepository(engine: MockEngine): AlbumsRepository =
        AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = MutationStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

    @Test
    fun remove_asset_from_album_calls_delete_with_asset_id() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repository = newRepository(engine)

        repository.removeAsset("alb-1", "ast-1")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/albums/alb-1/assets/ast-1", captured.single().second)
    }

    @Test
    fun set_album_cover_uses_put_with_asset_id_and_returns_summary() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(albumPayload("/api/assets/ast-1/thumbnail?size=Medium")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = newRepository(engine)

        val updated = repository.setCover("alb-1", "ast-1")
        assertEquals(HttpMethod.Put, captured.single().first)
        assertEquals("/api/albums/alb-1/cover", captured.single().second)
        assertTrue(updated.coverThumbnailUrl?.contains("ast-1") == true)
    }
}
