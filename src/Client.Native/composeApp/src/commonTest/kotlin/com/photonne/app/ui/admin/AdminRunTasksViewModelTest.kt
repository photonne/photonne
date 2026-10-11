package com.photonne.app.ui.admin

import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RunTasksStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminRunTasksViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Every view model a test made, so its coroutines (open streams, requests
    // still in flight) end with the test instead of resuming in the next one
    // under a different Main dispatcher.
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun runVmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
            created.clear()
        }
    }

    private val bus = AssetMutationBus()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AdminRunTasksViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = RunTasksStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return AdminRunTasksViewModel(
            AdminRepository(api),
            MapSettings(),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            bus,
        ).also { created += it }
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun finished(id: String, type: String) =
        """{"id":"$id","type":"$type","status":"Completed","percentage":100.0,"lastMessage":"ok",""" +
            """"startedAt":"2026-10-11T10:00:00Z","finishedAt":"2026-10-11T10:05:00Z"}"""

    @Test
    fun a_task_that_rewrites_the_library_tells_the_app_when_it_finishes() = runVmTest {
        // Emitted from whatever thread the response lands on: a channel fed by a
        // collector on the test dispatcher keeps the reads on the test thread.
        val events = Channel<AssetMutation>(Channel.UNLIMITED)
        val subscribed = CompletableDeferred<Unit>()
        backgroundScope.launch {
            bus.events.onSubscription { subscribed.complete(Unit) }.collect { events.send(it) }
        }
        subscribed.await()
        var tasks = "[${finished("T1", "IndexAssets")}]"
        val vm = viewModel { request ->
            if (request.url.encodedPath == "/api/tasks") json(tasks)
            else respond("", HttpStatusCode.NotFound)
        }

        vm.load()
        vm.state.first { it.tasksLoaded && !it.isLoading }
        runCurrent()
        // A run that had already finished before the screen looked is old news.
        assertTrue(events.tryReceive().isFailure)

        tasks = "[${finished("T1", "IndexAssets")},${finished("T2", "Maintenance")},${finished("T3", "Thumbnails")}]"
        vm.load()
        assertEquals(AssetMutation.AllChanged, events.receive())
        vm.state.first { it.backgroundTasks.size == 3 && !it.isLoading }

        // Listed again on the next poll: not announced twice.
        vm.load()
        vm.state.first { !it.isLoading }
        runCurrent()
        assertTrue(events.tryReceive().isFailure)
    }

    @Test
    fun a_failed_registry_read_is_shown_and_can_be_retried() = runVmTest {
        var fail = true
        val vm = viewModel { request ->
            when {
                request.url.encodedPath != "/api/tasks" -> respond("", HttpStatusCode.NotFound)
                fail -> respond("", HttpStatusCode.InternalServerError)
                else -> json("[]")
            }
        }

        vm.load()
        val failed = vm.state.first { it.tasksError != null && !it.isLoading }
        assertFalse(failed.tasksLoaded, "a failed read must not pass for an empty history")

        fail = false
        vm.retry()
        val recovered = vm.state.first { it.tasksLoaded }
        assertNull(recovered.tasksError)
    }
}
