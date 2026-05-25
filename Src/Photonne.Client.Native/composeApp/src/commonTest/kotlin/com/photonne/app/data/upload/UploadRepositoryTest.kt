package com.photonne.app.data.upload

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class UploadStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class UploadRepositoryTest {

    private fun newRepo(engine: MockEngine) = UploadRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = UploadStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun upload_posts_multipart_to_upload_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        var bodyContentType: String? = null
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            bodyContentType = request.body.contentType?.toString()
            respond(
                content = ByteReadChannel(
                    """{"message":"Asset uploaded successfully","assetId":"abc-123"}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val response = repo.upload(
            fileName = "vacation.jpg",
            mimeType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3, 4, 5)
        )

        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/assets/upload", captured.single().second)
        assertEquals("abc-123", response.assetId)
        assertNotNull(bodyContentType)
        assertTrue("multipart") { bodyContentType!!.startsWith("multipart/form-data") }
    }

    @Test
    fun upload_with_destination_still_posts_to_upload_endpoint() = runTest {
        // The pass-through contract: when DeviceBackupRepository calls
        // UploadRepository with destination = "mobile-backup", the request
        // still hits /api/assets/upload. The server-side test (UploadPipelineTests)
        // verifies that destination form field routes to the MobileBackup folder.
        var endpoint: String? = null
        val engine = MockEngine { request ->
            endpoint = request.url.encodedPath
            respond(
                content = ByteReadChannel(
                    """{"message":"Asset uploaded successfully","assetId":"abc-123"}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        repo.upload(
            fileName = "phone-shot.jpg",
            mimeType = "image/jpeg",
            bytes = byteArrayOf(9, 8, 7),
            destination = "mobile-backup"
        )

        assertEquals("/api/assets/upload", endpoint)
    }

    @Test
    fun upload_recognises_already_exists_response() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """{"message":"Asset already exists","assetId":"existing-1"}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val response = repo.upload(
            fileName = "dup.jpg",
            mimeType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3)
        )
        assertEquals("existing-1", response.assetId)
        assertTrue { response.message.contains("already exists", ignoreCase = true) }
    }
}
