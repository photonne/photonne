package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FavoriteStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class FavoriteSetTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repository(
        bus: AssetMutationBus = AssetMutationBus(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AssetDetailRepository {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = FavoriteStubTokenStorage(),
            authState = AuthStateHolder()
        )
        return AssetDetailRepository(api = PhotonneApiClient(client, "http://test.local"), mutationBus = bus)
    }

    private fun HttpRequestData.bodyJson() =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    @Test
    fun set_favorite_puts_the_value_and_returns_the_server_state() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            val asked = request.bodyJson()["isFavorite"]!!.jsonPrimitive.boolean
            respond(ByteReadChannel("""{"isFavorite":$asked}"""), HttpStatusCode.OK, json)
        }

        assertTrue(repository.setFavorite("aaa", true))
        assertEquals(false, repository.setFavorite("aaa", false))
        assertEquals(List(2) { "PUT /api/assets/aaa/favorite" }, calls)
    }

    @Test
    fun an_older_server_falls_back_to_the_toggle_and_lands_on_the_target() = runTest {
        var server = true // stale copy: the user saw an empty heart
        val calls = mutableListOf<HttpMethod>()
        val repository = repository { request ->
            calls += request.method
            if (request.method == HttpMethod.Put) {
                respond(ByteReadChannel(""), HttpStatusCode.MethodNotAllowed)
            } else {
                server = !server
                respond(ByteReadChannel("""{"isFavorite":$server}"""), HttpStatusCode.OK, json)
            }
        }

        assertTrue(repository.setFavorite("aaa", true))
        assertTrue(server)
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Post, HttpMethod.Post), calls)

        // Remembered: the next tap goes straight to the toggle.
        calls.clear()
        assertEquals(false, repository.setFavorite("aaa", false))
        assertEquals(listOf(HttpMethod.Post), calls)
    }

    @Test
    fun an_asset_not_found_is_an_error_not_an_older_server() = runTest {
        val calls = mutableListOf<HttpMethod>()
        val repository = repository { request ->
            calls += request.method
            respond(
                ByteReadChannel("""{"error":"Asset aaa not found","code":"asset_not_found"}"""),
                HttpStatusCode.NotFound,
                json
            )
        }

        val error = assertFailsWith<PhotonneApiException> { repository.setFavorite("aaa", true) }
        assertEquals(404, error.status)
        assertEquals(listOf(HttpMethod.Put), calls)
    }

    @Test
    fun set_favorites_sends_one_request_and_announces_the_confirmed_ones() = runTest {
        val bus = AssetMutationBus()
        val calls = mutableListOf<String>()
        val repository = repository(bus) { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            val ids = request.bodyJson()["assetIds"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(listOf("a", "b", "c"), ids)
            respond(ByteReadChannel("""{"assetIds":["a","c"],"isFavorite":true}"""), HttpStatusCode.OK, json)
        }
        val announced = mutableListOf<AssetMutation>()
        val collector = backgroundScope.launch { bus.events.collect { announced += it } }
        testScheduler.runCurrent()

        val confirmed = repository.setFavorites(listOf("a", "b", "c"), true)

        assertEquals(setOf("a", "c"), confirmed)
        assertEquals(listOf("PUT /api/assets/favorite"), calls)
        testScheduler.runCurrent()
        assertEquals<List<AssetMutation>>(
            listOf(AssetMutation.FavoriteChanged("a", true), AssetMutation.FavoriteChanged("c", true)),
            announced
        )
        collector.cancel()
    }

    @Test
    fun set_favorites_on_an_older_server_goes_one_by_one() = runTest {
        val favorites = mutableMapOf("a" to false, "b" to true, "c" to false)
        val repository = repository { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Put ->
                    respond(ByteReadChannel(""), HttpStatusCode.NotFound)
                path == "/api/assets/c/favorite" ->
                    respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                else -> {
                    val id = path.split('/')[3]
                    favorites[id] = !favorites.getValue(id)
                    respond(ByteReadChannel("""{"isFavorite":${favorites[id]}}"""), HttpStatusCode.OK, json)
                }
            }
        }

        val confirmed = repository.setFavorites(listOf("a", "b", "c"), true)

        assertEquals(setOf("a", "b"), confirmed)
        assertEquals(mapOf("a" to true, "b" to true, "c" to false), favorites)
    }
}
