package com.photonne.app.ui.upload

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.upload.UploadRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class UploadStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): UploadViewModel {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = UploadStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val api = PhotonneApiClient(client, "http://test.local")
        return UploadViewModel(
            UploadRepository(api),
            AlbumsRepository(api),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api))
        )
    }

    private fun MockRequestHandleScope.uploaded(assetId: String) = respond(
        content = ByteReadChannel("""{"message":"ok","assetId":"$assetId"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun picked(name: String, size: Int = 3) =
        PickedFile(name = name, mimeType = "image/jpeg", sizeBytes = size.toLong(), bytes = ByteArray(size))

    @Test
    fun oversize_and_unreadable_files_are_left_out_and_reported() = runTest {
        val vm = viewModel { uploaded("a-1") }

        vm.enqueue(
            listOf(
                picked("ok.jpg"),
                oversizePickedFile("huge.mov", "video/quicktime", UploadViewModel.MAX_BYTES_PER_FILE + 1),
                unreadablePickedFile("icloud.heic")
            )
        )

        val state = vm.state.value
        assertEquals(listOf("ok.jpg"), state.items.map { it.name })
        assertEquals(UploadViewModel.skippedFilesMessage(tooBig = 1, unreadable = 1), state.pickerError)
    }

    @Test
    fun skipped_message_covers_each_case() {
        assertNull(UploadViewModel.skippedFilesMessage(0, 0))
        assertEquals(
            "2 archivo(s) superan el límite de 200 MB y se han omitido.",
            UploadViewModel.skippedFilesMessage(2, 0)
        )
        assertEquals(
            "1 archivo(s) no se han podido leer y se han omitido.",
            UploadViewModel.skippedFilesMessage(0, 1)
        )
    }
}
