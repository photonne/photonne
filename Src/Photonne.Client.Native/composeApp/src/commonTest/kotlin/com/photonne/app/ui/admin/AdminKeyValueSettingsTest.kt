package com.photonne.app.ui.admin

import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.content.TextContent
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class SettingsStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private class TwoKeyViewModel(repository: AdminRepository) : AdminKeyValueSettingsViewModel(repository) {
    override val keys = listOf("A.Quality", "A.Name")
    override val defaults = mapOf("A.Quality" to "80", "A.Name" to "")
    override val intRanges = mapOf("A.Quality" to 1..100)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminKeyValueSettingsTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): TwoKeyViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = SettingsStubTokenStorage(),
            authState = AuthStateHolder()
        )
        return TwoKeyViewModel(AdminRepository(PhotonneApiClient(client, "http://test.local")))
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private suspend fun TwoKeyViewModel.settled(): AdminKeyValueUiState =
        state.first { !it.isLoading && !it.isSubmitting }

    @Test
    fun a_failed_read_fails_the_load_instead_of_showing_defaults() = runTest {
        val vm = viewModel { respond("", HttpStatusCode.InternalServerError) }

        vm.load()
        val state = vm.settled()

        assertTrue(state.loadFailed)
        assertTrue(state.current.isEmpty(), "defaults must not pass for server values")
        assertFalse(state.canSave)
    }

    @Test
    fun an_unset_key_falls_back_to_the_default() = runTest {
        val vm = viewModel { json("""{"key":"k","value":""}""") }

        vm.load()
        val state = vm.settled()

        assertFalse(state.loadFailed)
        assertEquals("80", state.get("A.Quality"))
        assertFalse(state.isDirty)
    }

    @Test
    fun an_edit_made_while_saving_stays_unsaved() = runTest {
        val gate = CompletableDeferred<Unit>()
        val posted = mutableListOf<String>()
        val vm = viewModel { request ->
            if (request.method == HttpMethod.Post) {
                posted += (request.body as TextContent).text
                gate.await()
            }
            json("""{"key":"k","value":""}""")
        }
        vm.load()
        vm.settled()

        vm.set("A.Quality", "90")
        vm.save()
        vm.set("A.Name", "typed during the request")
        gate.complete(Unit)
        val state = vm.settled()

        assertEquals(1, posted.size)
        assertTrue("A.Quality" in posted.single())
        assertEquals("90", state.original["A.Quality"])
        assertEquals("", state.original["A.Name"])
        assertTrue(state.isDirty, "the second edit never reached the server")
    }

    @Test
    fun out_of_range_and_empty_numbers_hold_save_back() = runTest {
        val vm = viewModel { json("""{"key":"k","value":""}""") }
        vm.load()
        vm.settled()

        vm.set("A.Quality", "500")
        assertEquals(setOf("A.Quality"), vm.state.value.invalid)
        assertFalse(vm.state.value.canSave)

        vm.set("A.Quality", "")
        assertFalse(vm.state.value.canSave)

        vm.set("A.Quality", "100")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun invalid_int_keys_ignores_keys_without_a_range() {
        val invalid = invalidIntKeys(
            current = mapOf("a" to "0", "b" to "99999999999", "c" to "free text", "d" to "7"),
            ranges = mapOf("a" to 1..100, "b" to 0..1_000_000, "d" to 1..32),
        )
        assertEquals(setOf("a", "b"), invalid)
    }

    @Test
    fun slider_values_snap_to_the_step_inside_the_range() {
        assertEquals(500, snapToStep(537f, 100..5000, 100))
        assertEquals(600, snapToStep(551f, 100..5000, 100))
        assertEquals(100, snapToStep(-40f, 100..5000, 100))
        assertEquals(5000, snapToStep(9000f, 100..5000, 100))
        assertEquals(17, snapToStep(16.6f, 1..32, 1))
    }
}
