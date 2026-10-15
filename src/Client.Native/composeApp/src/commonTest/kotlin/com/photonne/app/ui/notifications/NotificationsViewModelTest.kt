package com.photonne.app.ui.notifications

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.notifications.ActivityNotificationScheduler
import com.photonne.app.data.notifications.ActivityNotifications
import com.photonne.app.data.notifications.NotificationsRepository
import com.photonne.app.data.version.AppVersionStore
import com.photonne.app.ui.TrackedViewModels
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

private class NotificationsStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private var vm: NotificationsViewModel? = null

    // The unread-count poll loops forever: vms.runTest stops it (and waits for
    // it) inside the test, before tearDown swaps Dispatchers.Main back.
    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): NotificationsViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = NotificationsStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return NotificationsViewModel(
            NotificationsRepository(api),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            ActivityNotifications(
                MapSettings(),
                object : ActivityNotificationScheduler {},
                NotificationsStubTokenStorage()
            )
        ).also { vm = vms.track(it) }
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun item(id: String, isRead: Boolean = false) =
        """{"id":"$id","type":0,"isRead":$isRead,"createdAt":"2026-10-01T10:00:00Z"}"""

    private fun page(page: Int, totalPages: Int, vararg ids: String, unread: Int = 0) =
        """{"items":[${ids.joinToString(",") { item(it) }}],"totalCount":${totalPages * 2},""" +
            """"page":$page,"pageSize":20,"totalPages":$totalPages,"unreadCount":$unread}"""

    private suspend fun awaitState(predicate: (NotificationsUiState) -> Boolean): NotificationsUiState =
        // The mock engine answers on its own threads: wait in real time.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm!!.state.first(predicate) }
        }

    private fun HttpRequestData.isList() =
        method == HttpMethod.Get && url.encodedPath == "/api/notifications"

    private fun HttpRequestData.isUnreadCount() = url.encodedPath == "/api/notifications/unread-count"

    @Test
    fun refresh_goes_back_to_page_one() = vms.runTest {
        val vm = viewModel { request ->
            when {
                request.isUnreadCount() -> json("""{"count":0}""")
                request.url.parameters["page"] == "2" -> json(page(2, 2, "b"))
                else -> json(page(1, 2, "a"))
            }
        }
        vm.ensureLoaded()
        awaitState { it.loaded && !it.isLoading }
        vm.loadMore()
        awaitState { it.page == 2 && !it.isAppending }

        vm.refresh()

        val state = awaitState { it.page == 1 && !it.isLoading }
        assertEquals(listOf("a"), state.items.map { it.id })
    }

    @Test
    fun a_late_answer_for_the_previous_filter_is_dropped() = vms.runTest {
        val slowAll = CompletableDeferred<Unit>()
        val vm = viewModel { request ->
            when {
                request.isUnreadCount() -> json("""{"count":0}""")
                request.url.parameters["unreadOnly"] == "true" -> json(page(1, 1, "unread"))
                else -> {
                    slowAll.await()
                    json(page(1, 1, "all"))
                }
            }
        }
        vm.ensureLoaded()
        vm.setUnreadOnly(true)
        awaitState { it.loaded && it.items.map { n -> n.id } == listOf("unread") }

        slowAll.complete(Unit)

        // Nothing may replace the unread list now.
        val state = withContext(Dispatchers.Default) {
            kotlinx.coroutines.delay(300)
            vm.state.value
        }
        assertEquals(listOf("unread"), state.items.map { it.id })
    }

    @Test
    fun mark_read_is_undone_when_the_server_fails() = vms.runTest {
        val vm = viewModel { request ->
            when {
                request.isUnreadCount() -> json("""{"count":1}""")
                request.isList() -> json(page(1, 1, "n-1", unread = 1))
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }
        vm.ensureLoaded()
        awaitState { it.loaded && !it.isLoading }

        vm.markRead("n-1")

        val state = awaitState { it.error != null }
        assertFalse(state.items.single().isRead)
        assertEquals(1, state.unreadCount)
        assertNotNull(state.error)
    }
}
