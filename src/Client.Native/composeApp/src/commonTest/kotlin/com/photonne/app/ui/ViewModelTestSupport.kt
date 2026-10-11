package com.photonne.app.ui

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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

/**
 * Shared wiring for ViewModel tests: a real [PhotonneApiClient] over a
 * [MockEngine], so the repositories and the error mapping run as in the app.
 */
private class StubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

internal fun testApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): PhotonneApiClient = PhotonneApiClient(
    client = buildPhotonneHttpClient(
        engine = MockEngine(handler),
        baseUrl = "http://test.local",
        tokenStorage = StubTokenStorage(),
        authState = AuthStateHolder()
    ),
    baseUrl = "http://test.local"
)

internal fun testErrorFactory(api: PhotonneApiClient) =
    UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api))

internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = ByteReadChannel(body),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json")
)

/** Minimal asset as the server sends it in timeline-shaped lists. */
internal fun timelineItemJson(id: String) =
    """{"id":"$id","fileName":"$id.jpg","fullPath":"/$id.jpg","fileSize":1,""" +
        """"fileCreatedAt":"2026-01-01T00:00:00Z","fileModifiedAt":"2026-01-01T00:00:00Z",""" +
        """"extension":".jpg","scannedAt":"2026-01-01T00:00:00Z","type":"IMAGE"}"""
