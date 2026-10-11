package com.photonne.app.ui.admin

import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.version.AppVersionStore
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
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class LibrariesStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminLibrariesViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): AdminLibrariesViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = LibrariesStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return AdminLibrariesViewModel(
            AdminRepository(api),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api))
        )
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun runningScan(taskId: String, libraryId: String) =
        """[{"id":"$taskId","type":"LibraryScan","status":"Running","percentage":40.0,""" +
            """"lastMessage":"Indexing","startedAt":"2026-10-11T10:00:00Z",""" +
            """"parameters":{"libraryId":"$libraryId"}}]"""

    @Test
    fun cancel_stops_the_scan_on_the_server() = runTest {
        val stream = ByteChannel(autoFlush = true)
        val cancelled = mutableListOf<String>()
        val vm = viewModel { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Delete && path.startsWith("/api/tasks/") -> {
                    cancelled += path.removePrefix("/api/tasks/")
                    stream.close()
                    respond("", HttpStatusCode.NoContent)
                }
                path == "/api/libraries/L1/scan/stream" -> respond(
                    content = stream,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")
                )
                path == "/api/tasks" -> json("[]")
                else -> json("[]")
            }
        }

        vm.startScan("L1")
        stream.writeStringUtf8("""{"message":"Scanning","percentage":5,"taskId":"T1"}""" + "\n")
        vm.state.first { it.scanProgress?.taskId == "T1" }

        vm.cancelScan()
        val state = vm.state.first { it.scanningLibraryId == null && !it.isCancellingScan }

        assertEquals(listOf("T1"), cancelled)
        assertNull(state.scanProgress)
        assertNull(state.error)
    }

    @Test
    fun a_failed_cancel_keeps_the_scan_card() = runTest {
        val stream = ByteChannel(autoFlush = true)
        val vm = viewModel { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Delete -> respond("", HttpStatusCode.InternalServerError)
                path == "/api/libraries/L1/scan/stream" -> respond(
                    content = stream,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")
                )
                else -> json("[]")
            }
        }

        vm.startScan("L1")
        stream.writeStringUtf8("""{"message":"Scanning","percentage":5,"taskId":"T1"}""" + "\n")
        vm.state.first { it.scanProgress?.taskId == "T1" }

        vm.cancelScan()
        val state = vm.state.first { it.error != null }

        assertEquals("L1", state.scanningLibraryId)
        assertFalse(state.isCancellingScan)
        stream.close()
    }

    @Test
    fun entering_the_screen_follows_a_scan_already_running() = runTest {
        val stream = ByteChannel(autoFlush = true)
        val vm = viewModel { request ->
            val path = request.url.encodedPath
            when (path) {
                "/api/tasks" -> json(runningScan("T2", "L2"))
                "/api/tasks/T2/stream" -> respond(
                    content = stream,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")
                )
                else -> json("[]")
            }
        }

        vm.attachToRunningScan()
        val state = vm.state.first { it.scanningLibraryId != null }

        assertEquals("L2", state.scanningLibraryId)
        assertEquals("Indexing", state.scanProgress?.message)
        stream.writeStringUtf8("""{"message":"Replayed","percentage":40,"taskId":"T2"}""" + "\n")
        vm.state.first { it.scanProgress?.message == "Replayed" }
        stream.close()
    }

    @Test
    fun scanning_a_library_already_being_scanned_follows_that_scan() = runTest {
        val requested = mutableListOf<String>()
        val vm = viewModel { request ->
            val path = request.url.encodedPath
            requested += path
            when (path) {
                "/api/tasks" -> json(runningScan("T3", "L3"))
                "/api/tasks/T3/stream" -> json(
                    """{"message":"Done","percentage":100,"isCompleted":true,"taskId":"T3"}""" + "\n"
                )
                else -> json("[]")
            }
        }

        vm.startScan("L3")
        vm.state.first { it.statusMessage == "Done" }

        assertTrue("/api/tasks/T3/stream" in requested)
        assertFalse("/api/libraries/L3/scan/stream" in requested)
    }

    @Test
    fun a_grant_that_lands_after_the_sheet_moved_on_is_dropped() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path == "/api/libraries/A/permissions" -> {
                    gate.await()
                    json("""{"id":"p1","userId":"u1","username":"ana","email":"a@x","canRead":true}""")
                }
                else -> json("[]")
            }
        }
        vm.openPermissions("A", emptyList())
        vm.state.first { !it.permissionsLoading }

        vm.grantPermission("u1")
        vm.openPermissions("B", emptyList())
        vm.state.first { !it.permissionsLoading }
        gate.complete(Unit)
        // Let the grant's response land: it comes back on the engine's thread.
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(300) }

        assertTrue(vm.state.value.permissions.isEmpty(), "library A's grant must not show under B")
        assertNull(vm.state.value.statusMessage)
    }

    @Test
    fun a_failed_revoke_with_the_sheet_closed_goes_to_the_snackbar() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel { request ->
            if (request.method == HttpMethod.Delete) {
                gate.await()
                respond("", HttpStatusCode.InternalServerError)
            } else {
                json("[]")
            }
        }
        vm.openPermissions("A", emptyList())
        vm.state.first { !it.permissionsLoading }

        vm.revokePermission("u1")
        vm.closePermissions()
        gate.complete(Unit)
        val state = vm.state.first { it.statusMessage != null }

        assertNull(state.permissionsError)
        assertNull(state.permissionsLibraryId)
    }
}
