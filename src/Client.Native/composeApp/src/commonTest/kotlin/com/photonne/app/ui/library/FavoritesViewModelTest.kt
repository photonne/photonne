package com.photonne.app.ui.library

import com.photonne.app.data.album.AlbumsRepository
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FavoritesStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun itemJson(id: String, day: Int) = """
        {
          "id": "$id",
          "fileName": "$id.jpg",
          "fullPath": "/photos/$id.jpg",
          "fileSize": 10,
          "fileCreatedAt": "2026-01-${day.toString().padStart(2, '0')}T10:00:00Z",
          "fileModifiedAt": "2026-01-01T10:00:00Z",
          "extension": ".jpg",
          "scannedAt": "2026-01-01T10:00:00Z",
          "type": "IMAGE",
          "isFavorite": true
        }
    """.trimIndent()

    /**
     * Two pages of favorites: "a","b" (cursor → page two) and "c","d". The
     * server answers by cursor, so a reload of the first page alone would
     * lose "c" and "d".
     */
    private fun build(
        bus: AssetMutationBus,
        pageTwo: MutableList<String> = mutableListOf("c", "d"),
    ): Pair<FavoritesViewModel, MutableList<String?>> {
        val cursors = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val cursor = request.url.parameters["cursor"]
            cursors += cursor
            val body = if (cursor == null) {
                """{"items":[${itemJson("a", 20)},${itemJson("b", 19)}],"hasMore":true,"nextCursor":"2026-01-19T10:00:00Z"}"""
            } else {
                val items = pageTwo.mapIndexed { i, id -> itemJson(id, 18 - i) }.joinToString(",")
                """{"items":[$items],"hasMore":false,"nextCursor":null}"""
            }
            respond(
                ByteReadChannel(body),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = FavoritesStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        val vm = vms.track(FavoritesViewModel(
            assetRepository = AssetDetailRepository(api, bus),
            albumsRepository = AlbumsRepository(api),
            errorFactory = UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            mutationBus = bus,
        ))
        return vm to cursors
    }

    private suspend fun FavoritesViewModel.loadBothPages() {
        ensureLoaded()
        state.first { it.loaded && !it.isInitialLoading }
        // La carga inicial publica su estado un instante antes de que su
        // trabajo termine (en el hilo del MockEngine), y loadMore() no arranca
        // con un trabajo vivo: pedida justo entonces se perdía y el test se
        // quedaba esperando la segunda página. Se reintenta hasta que entra.
        while (state.value.items.size < 4 || state.value.isAppending) {
            loadMore()
            withContext(Dispatchers.Default) { delay(5) }
        }
    }

    @Test
    fun a_mutation_reloads_every_loaded_page_not_just_the_first() = vms.runTest {
        val bus = AssetMutationBus()
        val pageTwo = mutableListOf("c", "d")
        val (vm, cursors) = build(bus, pageTwo)
        vm.loadBothPages()
        cursors.clear()

        // "e" comes back from the trash into page two.
        pageTwo += "e"
        bus.emit(AssetMutation.Restored(listOf("e")))

        // Both pages come back again, swapped in at once; nothing collapses
        // to page one.
        val state = vm.state.first { it.items.size == 5 }
        assertEquals(listOf("a", "b", "c", "d", "e"), state.items.map { it.id })
        assertEquals(2, cursors.size)
    }

    @Test
    fun favoriting_again_puts_the_photo_back_where_it_was() = vms.runTest {
        val (vm, _) = build(AssetMutationBus())
        vm.loadBothPages()

        vm.setFavorite("b", false)
        assertEquals(listOf("a", "c", "d"), vm.state.value.items.map { it.id })

        vm.setFavorite("b", true)
        // The bus echo of the same change must not duplicate it.
        vm.setFavorite("b", true)
        assertEquals(listOf("a", "b", "c", "d"), vm.state.value.items.map { it.id })
    }
}
