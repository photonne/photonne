package com.photonne.app.data.asset

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class BulkStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class BulkAssetActionsTest {

    private fun newClient(engine: MockEngine) = buildPhotonneHttpClient(
        engine = engine,
        baseUrl = "http://test.local",
        tokenStorage = BulkStubTokenStorage(),
        authState = AuthStateHolder()
    )

    @Test
    fun archive_posts_to_assets_archive() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = AssetDetailRepository(api = PhotonneApiClient(newClient(engine), "http://test.local"))

        repo.archive(listOf("a", "b", "c"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/archive", captured.single().second)
    }

    @Test
    fun trash_posts_to_assets_delete() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = AssetDetailRepository(api = PhotonneApiClient(newClient(engine), "http://test.local"))

        repo.trash(listOf("a", "b"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/delete", captured.single().second)
    }

    @Test
    fun add_assets_batch_posts_to_album_assets_batch() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = AlbumsRepository(api = PhotonneApiClient(newClient(engine), "http://test.local"))

        repo.addAssetsBatch("alb-1", listOf("a", "b", "c"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/albums/alb-1/assets/batch", captured.single().second)
    }

    @Test
    fun empty_id_lists_are_no_ops() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val client = newClient(engine)
        val assets = AssetDetailRepository(api = PhotonneApiClient(client, "http://test.local"))
        val albums = AlbumsRepository(api = PhotonneApiClient(client, "http://test.local"))

        assets.archive(emptyList())
        assets.trash(emptyList())
        albums.addAssetsBatch("alb-1", emptyList())

        assertEquals(0, captured.size)
    }
}
