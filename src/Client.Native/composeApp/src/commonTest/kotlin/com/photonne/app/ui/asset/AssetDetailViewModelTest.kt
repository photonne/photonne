package com.photonne.app.ui.asset

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.version.AppVersionStore
import com.photonne.app.ui.TrackedViewModels
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class DetailStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Fake server holding one asset's favorite flag; POST /favorite toggles it. */
    private class FakeServer {
        var favorite = false
        var favoritePosts = 0
        /** When set, every POST /favorite parks until it completes. */
        var postGate: CompletableDeferred<Unit>? = null
    }

    private fun detailJson(isFavorite: Boolean) = """
        {
          "id": "a",
          "fileName": "a.jpg",
          "fullPath": "/photos/a.jpg",
          "fileSize": 10,
          "fileCreatedAt": "2026-01-01T10:00:00Z",
          "fileModifiedAt": "2026-01-01T10:00:00Z",
          "extension": ".jpg",
          "scannedAt": "2026-01-01T10:00:00Z",
          "type": "IMAGE",
          "isFavorite": $isFavorite
        }
    """.trimIndent()

    private fun build(server: FakeServer, bus: AssetMutationBus): AssetDetailViewModel {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.method == HttpMethod.Post && path == "/api/assets/a/favorite" -> {
                    server.postGate?.await()
                    server.favoritePosts++
                    server.favorite = !server.favorite
                    respond(ByteReadChannel("""{"isFavorite":${server.favorite}}"""), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Get && path == "/api/assets/a" ->
                    respond(ByteReadChannel(detailJson(server.favorite)), HttpStatusCode.OK, json)
                // Faces, same day…: the panel extras degrade to empty.
                else -> respond(ByteReadChannel(""), HttpStatusCode.NotFound)
            }
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = DetailStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return vms.track(AssetDetailViewModel(
            repository = AssetDetailRepository(api, bus),
            errorFactory = UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            mutationBus = bus,
        ))
    }

    @Test
    fun favorite_changed_on_the_bus_updates_the_cached_detail() = vms.runTest {
        val server = FakeServer()
        val bus = AssetMutationBus()
        val vm = build(server, bus)
        vm.select("a")
        vm.state.first { it.detail?.id == "a" }
        assertFalse(vm.state.value.detail!!.isFavorite)

        // Bulk favorite from a grid while the viewer was closed.
        server.favorite = true
        bus.emit(AssetMutation.FavoriteChanged("a", true))

        vm.state.first { it.detail?.isFavorite == true }
        assertEquals(true, vm.details.value["a"]?.isFavorite)
    }

    @Test
    fun toggle_on_a_stale_copy_still_lands_on_the_heart_the_user_asked_for() = vms.runTest {
        val server = FakeServer()
        val vm = build(server, AssetMutationBus())
        vm.select("a")
        vm.state.first { it.detail?.id == "a" }

        // Server changed behind our back (no event): we show an empty heart.
        server.favorite = true
        val committed = CompletableDeferred<Boolean>()
        vm.toggleFavorite("a") { committed.complete(it) }

        assertTrue(committed.await())
        assertTrue(server.favorite)
        assertEquals(2, server.favoritePosts)
        assertTrue(vm.state.value.detail!!.isFavorite)
    }

    @Test
    fun taps_while_a_favorite_request_is_out_are_ignored() = vms.runTest {
        val server = FakeServer()
        val vm = build(server, AssetMutationBus())
        vm.select("a")
        vm.state.first { it.detail?.id == "a" }

        val gate = CompletableDeferred<Unit>()
        server.postGate = gate
        val committed = CompletableDeferred<Boolean>()
        vm.toggleFavorite("a") { committed.complete(it) }
        vm.toggleFavorite("a") { error("second toggle must be ignored") }
        gate.complete(Unit)

        assertTrue(committed.await())
        assertEquals(1, server.favoritePosts)
        assertTrue(server.favorite)
    }

    @Test
    fun restored_on_the_bus_refetches_the_visible_detail() = vms.runTest {
        val server = FakeServer()
        val bus = AssetMutationBus()
        val vm = build(server, bus)
        vm.select("a")
        vm.state.first { it.detail?.id == "a" }

        server.favorite = true
        bus.emit(AssetMutation.Restored(listOf("a")))

        vm.state.first { it.detail?.isFavorite == true }
    }
}
