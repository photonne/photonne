package com.photonne.app.ui.upload

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.devicebackup.UploadFailureReason
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.Buffer
import kotlinx.io.Source
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

    private val bus = AssetMutationBus()

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
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            bus
        )
    }

    private fun MockRequestHandleScope.uploaded(assetId: String) = respond(
        content = ByteReadChannel("""{"message":"ok","assetId":"$assetId"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    /** In-memory stand-in for a picked file, counting how it's opened and released. */
    private class FakeContent(private val bytes: ByteArray) : PickedContent {
        var opened = 0
        var released = 0
        override suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T {
            opened++
            return block(Buffer().apply { write(bytes) }, bytes.size.toLong())
        }
        override fun release() {
            released++
        }
    }

    private fun picked(name: String, size: Int = 3, content: PickedContent = FakeContent(ByteArray(size))) =
        PickedFile(name = name, mimeType = "image/jpeg", sizeBytes = 0L, content = content)

    @Test
    fun unreadable_files_are_left_out_and_reported() = runTest {
        val vm = viewModel { uploaded("a-1") }

        vm.enqueue(listOf(picked("ok.jpg"), unreadablePickedFile("icloud.heic")))

        val state = vm.state.value
        assertEquals(listOf("ok.jpg"), state.items.map { it.name })
        assertEquals("1 archivo(s) no se han podido leer y se han omitido.", state.pickerError)
        assertNull(UploadViewModel.skippedFilesMessage(0))
        // The streamed upload finishes off the test thread: let the queue
        // drain before tearDown swaps Dispatchers.Main back.
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { !it.isUploading } } }
    }

    @Test
    fun the_file_is_streamed_from_its_content_and_released_once_uploaded() = runTest {
        var sentBody = 0L
        val vm = viewModel { request ->
            sentBody = request.body.contentLength ?: -1L
            uploaded("a-1")
        }
        val content = FakeContent(ByteArray(5))

        vm.enqueue(listOf(picked("ok.jpg", content = content)))

        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.state.first { st -> !st.isUploading && st.items.single().status == UploadStatus.Done }
            }
        }
        assertEquals(1, content.opened)
        assertEquals(1, content.released)
        // The size the content reports replaces the picker's unknown 0.
        assertEquals(5L, state.items.single().sizeBytes)
        assertEquals(true, sentBody > 5L)
    }

    @Test
    fun cancel_all_marks_the_upload_cancelled_not_failed() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel { gate.await(); uploaded("a-1") }

        val contents = listOf(FakeContent(ByteArray(3)), FakeContent(ByteArray(3)))
        vm.enqueue(listOf(picked("one.jpg", content = contents[0]), picked("two.jpg", content = contents[1])))
        assertEquals(UploadStatus.Uploading, vm.state.value.items.first().status)

        vm.cancelAll()

        // Cancelled items can't be retried, so what the picker kept goes now.
        assertEquals(listOf(1, 1), contents.map { it.released })

        val state = vm.state.value
        assertEquals(listOf(UploadStatus.Cancelled, UploadStatus.Cancelled), state.items.map { it.status })
        assertNull(state.lastBatch)
        assertEquals(false, state.isUploading)
    }

    @Test
    fun removing_the_upload_in_flight_aborts_it_and_moves_on() = runTest {
        val neverAnswers = CompletableDeferred<Unit>()
        val vm = viewModel { neverAnswers.await(); uploaded("a-1") }

        vm.enqueue(listOf(picked("one.jpg"), picked("two.jpg")))
        val first = vm.state.value.items.first()
        assertEquals(UploadStatus.Uploading, first.status)

        vm.remove(first.id)

        // The first request never answers: the queue only gets to the second
        // one because removing the first aborted it. Waited in real time, off
        // the test's virtual clock, since the mock engine runs on its own threads.
        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.state.first { st -> st.items.singleOrNull()?.status == UploadStatus.Uploading }
            }
        }
        assertEquals(listOf("two.jpg"), state.items.map { it.name })
        vm.cancelAll()
    }

    @Test
    fun a_drained_batch_reports_its_count_and_tells_the_app() = runTest {
        val vm = viewModel { uploaded("a-1") }
        // Subscribed before enqueueing: neither flow replays.
        val landed = async(start = CoroutineStart.UNDISPATCHED) { vm.batchLanded.first() }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { bus.events.first() }

        vm.enqueue(listOf(picked("one.jpg")))

        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                assertEquals(1, landed.await())
                assertEquals(AssetMutation.AllChanged, mutation.await())
            }
        }
    }

    private suspend fun failedItemAfter(status: HttpStatusCode, body: String, contentType: String): UploadItem {
        val vm = viewModel {
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType)
            )
        }
        vm.enqueue(listOf(picked("big.mov")))
        return withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.state.first { st -> !st.isUploading && st.items.single().status == UploadStatus.Failed }
            }
        }.items.single()
    }

    @Test
    fun photonne_size_limit_shows_its_own_message_with_the_megabytes() = runTest {
        val item = failedItemAfter(
            HttpStatusCode.PayloadTooLarge,
            """{"error":"El archivo supera el tamaño máximo permitido (100 MB).","code":"upload_too_large"}""",
            "application/json"
        )
        assertEquals(UploadFailureReason.FileTooLarge, item.failureReason)
        assertEquals("El archivo supera el tamaño máximo permitido (100 MB).", item.errorMessage)
    }

    @Test
    fun a_proxy_413_is_told_apart_from_the_photonne_limit() = runTest {
        val item = failedItemAfter(
            HttpStatusCode.PayloadTooLarge,
            "<html><head><title>413 Request Entity Too Large</title></head><body><center><h1>413 Request Entity Too Large</h1></center><hr><center>nginx</center></body></html>",
            "text/html"
        )
        assertEquals(UploadFailureReason.ProxyTooLarge, item.failureReason)
    }
}
