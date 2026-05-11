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
    fun suggestions_hits_suggestions_endpoint() = runTest {
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
        repo.suggestions("p-1", limit = 10, offset = 0)
        val url = captured.single().second
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path") { url.contains("/api/people/p-1/suggestions") }
        assertTrue("limit") { url.contains("limit=10") }
    }

    @Test
    fun accept_all_suggestions_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"affected":3}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val result = repo.acceptAllSuggestions("p-1")
        assertEquals(3, result.affected)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/suggestions/accept-all", captured.single().second)
    }

    @Test
    fun dismiss_all_suggestions_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"affected":2}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val result = repo.dismissAllSuggestions("p-1")
        assertEquals(2, result.affected)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/suggestions/dismiss-all", captured.single().second)
    }

    @Test
    fun accept_face_suggestion_posts_per_face() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"id":"f-1","personId":"p-1"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val result = repo.acceptFaceSuggestion("f-1")
        assertEquals("p-1", result.personId)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/faces/f-1/accept-suggestion", captured.single().second)
    }

    @Test
    fun assign_face_to_person_posts_personId_body() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"id":"f-1","personId":"p-2"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        repo.assignFaceToPerson("f-1", "p-2")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/faces/f-1/assign", captured.single().second)
    }

    @Test
    fun reject_face_calls_delete() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)
        repo.rejectFace("f-1")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/faces/f-1", captured.single().second)
    }

    @Test
    fun unassign_face_posts_to_unassign_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)
        repo.unassignFace("f-1")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/faces/f-1/unassign", captured.single().second)
    }

    @Test
    fun set_cover_face_posts_to_cover_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)
        repo.setCoverFace("p-1", "f-1")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/cover/f-1", captured.single().second)
    }

    @Test
    fun merge_posts_to_merge_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)
        repo.merge(targetPersonId = "p-1", sourcePersonId = "p-2")
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/merge/p-2", captured.single().second)
    }

    @Test
    fun recluster_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"personsCreated":4}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val result = repo.recluster()
        assertEquals(4, result.personsCreated)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/recluster", captured.single().second)
    }

    @Test
    fun unlink_asset_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""{"facesDetached":1}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)
        val result = repo.unlinkAsset("p-1", "a-1")
        assertEquals(1, result.facesDetached)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/people/p-1/assets/a-1/unlink", captured.single().second)
    }

    @Test
    fun asset_faces_hits_assets_endpoint() = runTest {
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
        repo.assetFaces("a-1")
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/assets/a-1/faces", captured.single().second)
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
