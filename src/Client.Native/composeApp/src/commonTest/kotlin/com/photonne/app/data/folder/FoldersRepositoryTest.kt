package com.photonne.app.data.folder

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

private class FoldersStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class FoldersRepositoryTest {

    private fun newRepo(engine: MockEngine) = FoldersRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = FoldersStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun list_folders_parses_payload() = runTest {
        val payload = """[
          {
            "id": "11111111-1111-1111-1111-111111111111",
            "path": "/photos/2026",
            "name": "2026",
            "parentFolderId": null,
            "createdAt": "2026-05-09T08:00:00Z",
            "assetCount": 142,
            "previewAssetIds": [],
            "isShared": false,
            "isOwner": true,
            "sharedWithCount": 0
          }
        ]""".trimIndent()
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val folders = repo.list()
        assertEquals(1, folders.size)
        assertEquals("2026", folders[0].name)
        assertEquals(142, folders[0].assetCount)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/folders", captured.single().second)
    }

    @Test
    fun folder_assets_uses_dedicated_endpoint() = runTest {
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
        val items = repo.assets("alb-1")
        assertTrue(items.isEmpty())
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/folders/alb-1/assets", captured.single().second)
    }
}
