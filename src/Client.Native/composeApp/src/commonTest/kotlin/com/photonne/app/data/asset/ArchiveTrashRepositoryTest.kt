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
import kotlin.test.assertTrue

private class ArchiveTrashStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class ArchiveTrashRepositoryTest {

    private fun newRepo(engine: MockEngine) = AssetDetailRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = ArchiveTrashStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun list_archived_calls_archived_endpoint_with_page_size() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("""{"items":[],"hasMore":false,"nextCursor":null}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val page = repo.listArchived(cursor = null, pageSize = 50)
        assertTrue(page.items.isEmpty())
        assertEquals(false, page.hasMore)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path") { captured.single().second.contains("/api/assets/archived") }
        assertTrue("pageSize") { captured.single().second.contains("pageSize=50") }
    }

    @Test
    fun list_trashed_calls_trash_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"items":[],"hasMore":false,"nextCursor":null}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.listTrashed()
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/assets/trash", captured.single().second)
    }

    @Test
    fun unarchive_posts_asset_ids() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.unarchive(listOf("a-1", "a-2"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/unarchive", captured.single().second)
    }

    @Test
    fun unarchive_all_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.unarchiveAll()
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/archive/unarchive-all", captured.single().second)
    }

    @Test
    fun restore_posts_asset_ids() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.restore(listOf("a-1"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/restore", captured.single().second)
    }

    @Test
    fun restore_all_trash_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.restoreAllTrash()
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/trash/restore-all", captured.single().second)
    }

    @Test
    fun purge_posts_asset_ids() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.purge(listOf("a-1"))
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/purge", captured.single().second)
    }

    @Test
    fun empty_trash_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.emptyTrash()
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/trash/empty", captured.single().second)
    }

    @Test
    fun list_favorites_calls_favorites_endpoint_with_page_size() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("""{"items":[],"hasMore":false,"nextCursor":null}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val page = repo.listFavorites(cursor = null, pageSize = 50)
        assertTrue(page.items.isEmpty())
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path") { captured.single().second.contains("/api/assets/favorites") }
        assertTrue("pageSize") { captured.single().second.contains("pageSize=50") }
    }
}
