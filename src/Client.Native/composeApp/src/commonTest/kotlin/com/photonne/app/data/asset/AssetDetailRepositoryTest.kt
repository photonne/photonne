package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class StubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class AssetDetailRepositoryTest {

    private val payload = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "fileName": "DSC_0042.jpg",
          "fullPath": "/photos/2026/05/DSC_0042.jpg",
          "fileSize": 5242880,
          "fileCreatedAt": "2026-05-01T18:30:00Z",
          "fileModifiedAt": "2026-05-01T18:30:00",
          "extension": ".jpg",
          "scannedAt": "2026-05-02T10:00:00Z",
          "type": "IMAGE",
          "checksum": "sha256:deadbeef",
          "hasExif": true,
          "hasThumbnails": true,
          "folderId": "22222222-2222-2222-2222-222222222222",
          "folderPath": "/photos/2026/05",
          "exif": {
            "dateTaken": "2026-05-01T17:55:00Z",
            "cameraMake": "Sony",
            "cameraModel": "A7 IV",
            "width": 6000,
            "height": 4000,
            "iso": 400,
            "aperture": 2.8,
            "shutterSpeed": 0.004,
            "focalLength": 35.0,
            "latitude": 41.38,
            "longitude": 2.17
          },
          "thumbnails": [
            {"id":"a","size":"Small","width":220,"height":146,"assetId":"11111111-1111-1111-1111-111111111111"},
            {"id":"b","size":"Medium","width":640,"height":426,"assetId":"11111111-1111-1111-1111-111111111111"}
          ],
          "tags": ["beach","sunset"],
          "syncStatus": 0,
          "isFavorite": true,
          "isArchived": false,
          "isFileMissing": false,
          "caption": "Atardecer en la Barceloneta",
          "isReadOnly": false
        }
    """.trimIndent()

    @Test
    fun parses_full_asset_detail_payload() = runTest {
        val captured = mutableListOf<String>()
        val engine = MockEngine { request ->
            captured += request.url.encodedPath
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = StubTokenStorage(),
            authState = AuthStateHolder()
        )
        val repository = AssetDetailRepository(api = PhotonneApiClient(client, "http://test.local"))

        val detail = repository.getDetail("11111111-1111-1111-1111-111111111111")
        assertEquals("DSC_0042.jpg", detail.fileName)
        assertEquals(5_242_880L, detail.fileSize)
        assertTrue(detail.isFavorite)
        assertEquals(2, detail.thumbnails.size)
        assertNotNull(detail.exif)
        assertEquals("Sony A7 IV", detail.exif?.cameraDisplay)
        assertEquals(400, detail.exif?.iso)
        assertEquals(2.17, detail.exif?.longitude)
        assertEquals(listOf("beach", "sunset"), detail.tags)
        assertEquals("Atardecer en la Barceloneta", detail.caption)
        assertEquals("/api/assets/11111111-1111-1111-1111-111111111111", captured.single())
    }
}
