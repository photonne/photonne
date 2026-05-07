package com.photonne.native.data.api

import com.photonne.native.data.auth.AuthState
import com.photonne.native.data.auth.AuthStateHolder
import com.photonne.native.data.auth.TokenStorage
import com.photonne.native.data.models.RefreshTokenRequest
import com.photonne.native.data.models.RefreshTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.client.request.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

const val SKIP_AUTH_HEADER = "X-Photonne-Skip-Auth"

internal val photonneJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun HttpRequestBuilder.skipAuthRefresh() {
    headers { append(SKIP_AUTH_HEADER, "1") }
}

/**
 * Refresh-on-401 mirror of Photonne.Client.Web/Services/AuthRefreshHandler.cs.
 * Uses a mutex so concurrent 401s only trigger one refresh.
 */
fun buildPhotonneHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenStorage: TokenStorage,
    authState: AuthStateHolder
): HttpClient {
    val refreshMutex = Mutex()

    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(photonneJson) }
    }

    client.plugin(HttpSend).intercept { request ->
        if (request.headers[SKIP_AUTH_HEADER] != null) {
            request.headers.remove(SKIP_AUTH_HEADER)
            return@intercept execute(request)
        }

        tokenStorage.getAccessToken()?.let { token ->
            request.headers[HttpHeaders.Authorization] = "Bearer $token"
        }

        val firstCall = execute(request)
        if (firstCall.response.status != HttpStatusCode.Unauthorized) return@intercept firstCall

        val refreshed = refreshMutex.withLock {
            attemptRefresh(client, baseUrl, tokenStorage)
        }
        if (!refreshed) {
            tokenStorage.clear()
            authState.update(AuthState.Unauthenticated)
            return@intercept firstCall
        }

        val retry = HttpRequestBuilder().takeFrom(request)
        tokenStorage.getAccessToken()?.let { token ->
            retry.headers[HttpHeaders.Authorization] = "Bearer $token"
        }
        execute(retry)
    }

    return client
}

private suspend fun attemptRefresh(
    client: HttpClient,
    baseUrl: String,
    tokenStorage: TokenStorage
): Boolean {
    val refreshToken = tokenStorage.getRefreshToken() ?: return false
    val deviceId = tokenStorage.getDeviceId()
    return try {
        val response: HttpResponse = client.post("$baseUrl/api/auth/refresh") {
            skipAuthRefresh()
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken = refreshToken, deviceId = deviceId))
        }
        if (response.status != HttpStatusCode.OK) return false
        val body: RefreshTokenResponse = response.body()
        tokenStorage.saveTokens(body.token, body.refreshToken)
        true
    } catch (_: Throwable) {
        false
    }
}
