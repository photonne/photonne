package com.photonne.app.data.actions

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.DownloadFormat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FormatStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

/**
 * The wire side of "original or JPG?": what the app asks the server and how it
 * says which format it wants.
 */
class DownloadFormatTest {

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PhotonneApiClient(
            buildPhotonneHttpClient(
                engine = MockEngine(handler),
                baseUrl = "http://test.local",
                tokenStorage = FormatStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            "http://test.local"
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun options_are_asked_with_the_selection_and_parsed() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        val api = api { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond(
                content = ByteReadChannel("""{"total":3,"convertibleCount":2,"extensions":["dng","heic"]}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val options = api.getDownloadOptions(listOf("a", "b", "c"))

        assertEquals(HttpMethod.Post, method)
        assertEquals("/api/assets/download-options", path)
        assertEquals("""{"assetIds":["a","b","c"]}""", body)
        assertEquals(3, options.total)
        assertEquals(2, options.convertibleCount)
        assertEquals(listOf("dng", "heic"), options.extensions)
    }

    @Test
    fun a_server_without_the_endpoint_fails_the_call() = runTest {
        // The view-model takes this as "nothing to ask" and downloads as before.
        val api = api { respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) }

        assertFailsWith<Throwable> { api.getDownloadOptions(listOf("a")) }
    }

    @Test
    fun a_single_download_says_the_format() = runTest {
        var format: String? = null
        var download: String? = null
        val api = api { request ->
            format = request.url.parameters["format"]
            download = request.url.parameters["download"]
            respond(
                content = ByteReadChannel(byteArrayOf(1, 2, 3)),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("image/jpeg"),
                    HttpHeaders.ContentDisposition to listOf("attachment; filename=IMG_0042.jpg")
                )
            )
        }

        val content = api.getAssetContent("a", DownloadFormat.Jpeg)

        assertEquals("jpeg", format)
        assertEquals("true", download)
        assertEquals("IMG_0042.jpg", content.suggestedFileName)
        assertEquals("image/jpeg", content.mimeType)
    }

    @Test
    fun a_single_download_without_a_choice_sends_no_format() = runTest {
        var hasFormat = true
        val api = api { request ->
            hasFormat = request.url.parameters.contains("format")
            respond(content = ByteReadChannel(byteArrayOf(1)), status = HttpStatusCode.OK)
        }

        api.getAssetContent("a")

        assertFalse(hasFormat)
    }

    @Test
    fun a_zip_says_the_format_in_the_body() = runTest {
        var body = ""
        val api = api { request ->
            body = request.body.toByteArray().decodeToString()
            respond(content = ByteReadChannel(byteArrayOf(1)), status = HttpStatusCode.OK)
        }

        api.downloadAssetsZip(listOf("a", "b"), fileName = "sel", format = DownloadFormat.Original)

        assertTrue(body.contains(""""format":"original""""), body)
    }

    @Test
    fun a_zip_without_a_choice_leaves_the_format_out() = runTest {
        // A server from before the choice existed must see the request it always saw.
        var body: String? = null
        val api = api { request ->
            body = request.body.toByteArray().decodeToString()
            respond(content = ByteReadChannel(byteArrayOf(1)), status = HttpStatusCode.OK)
        }

        api.downloadAssetsZip(listOf("a", "b"), fileName = "sel")

        assertFalse(body!!.contains("format"), body)
    }
}
