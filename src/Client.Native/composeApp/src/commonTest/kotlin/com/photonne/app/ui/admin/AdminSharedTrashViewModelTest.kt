package com.photonne.app.ui.admin

import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.version.AppVersionStore
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class SharedTrashStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSharedTrashViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AdminSharedTrashViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = SharedTrashStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return AdminSharedTrashViewModel(
            AdminRepository(api),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            AssetMutationBus(),
        )
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun page(vararg ids: String, hasMore: Boolean) =
        """{"items":[${ids.joinToString(",") { """{"id":"$it"}""" }}],"hasMore":$hasMore,""" +
            """"nextCursor":${if (hasMore) "\"2026-10-01T00:00:00Z\"" else "null"}}"""

    @Test
    fun a_reload_drops_the_page_a_pending_load_more_brings_back() = runTest {
        val olderPage = CompletableDeferred<Unit>()
        var firstPage = page("a", "b", hasMore = true)
        val vm = viewModel { request ->
            if (request.url.parameters["cursor"] != null) {
                olderPage.await()
                json(page("old-c", hasMore = false))
            } else {
                json(firstPage)
            }
        }
        vm.load()
        vm.state.first { it.loaded && !it.isLoading }

        vm.loadMore()
        firstPage = page("x", "y", hasMore = false)
        vm.load()
        vm.state.first { it.items.map { item -> item.id } == listOf("x", "y") && !it.isLoading }
        olderPage.complete(Unit)
        // Let the stale page land, if anything were still waiting for it.
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(300) }

        val state = vm.state.value
        assertEquals(listOf("x", "y"), state.items.map { it.id })
        assertEquals(false, state.isAppending)
    }
}
