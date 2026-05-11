package com.photonne.app.data.search

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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SearchStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class SearchRepositoryTest {

    private fun newRepo(engine: MockEngine) = SearchRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = SearchStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun text_search_forwards_all_query_params() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("""{"items":[],"hasMore":false}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.textSearch(
            query = "beach",
            from = LocalDate(2026, 1, 1),
            to = LocalDate(2026, 5, 1),
            personIds = listOf("p-1", "p-2"),
            objectLabels = listOf("dog"),
            sceneLabels = listOf("beach", "sunset")
        )

        val url = captured.single().second
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("path is search") { url.contains("/api/assets/search") }
        assertTrue("q") { url.contains("q=beach") }
        assertTrue("from") { url.contains("from=2026-01-01") }
        assertTrue("to") { url.contains("to=2026-05-01") }
        assertTrue("personId p-1") { url.contains("personId=p-1") }
        assertTrue("personId p-2") { url.contains("personId=p-2") }
        assertTrue("objectLabel dog") { url.contains("objectLabel=dog") }
        assertTrue("sceneLabel beach") { url.contains("sceneLabel=beach") }
        assertTrue("sceneLabel sunset") { url.contains("sceneLabel=sunset") }
    }

    @Test
    fun semantic_search_hits_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.toString()
            respond(
                content = ByteReadChannel("""{"items":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.semanticSearch("perro en la playa", limit = 25)

        val url = captured.single().second
        assertEquals(HttpMethod.Get, captured.single().first)
        assertTrue("semantic path") { url.contains("/api/assets/search/semantic") }
        assertTrue("query encoded") { url.contains("q=perro") }
        assertTrue("limit") { url.contains("limit=25") }
    }

    @Test
    fun object_labels_request_uses_objects_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""[{"label":"dog","assetCount":3}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val labels = repo.objectLabels(limit = 5)
        assertEquals(1, labels.size)
        assertEquals("dog", labels[0].label)
        assertEquals(3, labels[0].assetCount)
        assertEquals("/api/objects/labels", captured.single().second)
    }

    @Test
    fun scene_labels_request_uses_scenes_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("""[{"label":"beach","assetCount":12}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val labels = repo.sceneLabels()
        assertEquals(1, labels.size)
        assertEquals("beach", labels[0].label)
        assertEquals("/api/scenes/labels", captured.single().second)
    }

    @Test
    fun people_request_returns_items_only() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(
                    """{"total":1,"items":[{"id":"p-1","name":"Marc","faceCount":42,"isHidden":false,"pendingSuggestionsCount":0}]}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val people = repo.people()
        assertEquals(1, people.size)
        assertEquals("Marc", people[0].name)
        assertEquals(42, people[0].faceCount)
        assertEquals("/api/people", captured.single().second)
    }
}
