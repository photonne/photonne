package com.photonne.app.data.people

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

private class PeopleStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class PeopleRepositoryTest {

    private fun newRepo(engine: MockEngine) = PeopleRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = PeopleStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun list_forwards_include_hidden_and_pagination() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("""{"total":0,"items":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.list(includeHidden = true, limit = 30, offset = 60)
        val url = captured.single().second
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path") { url.contains("/api/people") }
        assertTrue("includeHidden") { url.contains("includeHidden=true") }
        assertTrue("limit") { url.contains("limit=30") }
        assertTrue("offset") { url.contains("offset=60") }
    }

    @Test
    fun rename_patches_with_trimmed_body() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.rename("p-1", "  Marc  ")
        assertEquals(HttpMethod.Patch, captured.single().first)
        assertEquals("/api/people/p-1", captured.single().second)
    }

    @Test
    fun hide_posts_to_hide_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.hide("p-1")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/hide", captured.single().second)
    }

    @Test
    fun unhide_posts_to_unhide_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.unhide("p-1")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/unhide", captured.single().second)
    }

    @Test
    fun assets_decodes_response_payload() = runTest {
        val payload = """
            {
              "total": 1,
              "items": [
                {
                  "id": "a-1",
                  "fileName": "selfie.jpg",
                  "type": "Image",
                  "fileCreatedAt": "2026-01-01T00:00:00Z",
                  "hasThumbnails": true,
                  "dominantColor": "#aabbcc"
                }
              ]
            }
        """.trimIndent()
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val page = repo.assets("p-1", limit = 50, offset = 0)
        assertEquals(1, page.total)
        assertEquals("a-1", page.items.single().id)
        assertEquals(true, page.items.single().hasThumbnails)
        val url = captured.single().second
        assertTrue("path") { url.contains("/api/search/people/p-1/assets") }
        assertTrue("limit") { url.contains("limit=50") }
    }
}
