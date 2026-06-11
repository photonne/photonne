package com.photonne.app.data.api

import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.RefreshTokenRequest
import com.photonne.app.data.models.RefreshTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
): HttpClient = buildPhotonneHttpClient(engine, { baseUrl }, tokenStorage, authState)

fun buildPhotonneHttpClient(
    engine: HttpClientEngine,
    baseUrlProvider: () -> String,
    tokenStorage: TokenStorage,
    authState: AuthStateHolder
): HttpClient {
    val refreshMutex = Mutex()

    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(photonneJson) }
        // Required so streaming endpoints (admin task buffers, library
        // scans, index/thumbnail/metadata) can override the per-request
        // timeouts. The Darwin engine has a 60 s `timeoutIntervalForRequest`
        // by default — which kills a `/api/tasks/{id}/stream` connection
        // any time the server goes 60 s between pushes (perfectly normal
        // during the scanning phase of a large indexing run).
        //
        // Only an idle socket timeout is set by default — deliberately NOT a
        // total requestTimeoutMillis, which would cap large transfers. The
        // socket timeout is the max gap *between data packets* (OkHttp's
        // read timeout; Darwin's timeoutIntervalForRequest, which resets on
        // every new byte), so a continuous download is never cut short. What
        // it does catch is a half-open socket: a pooled connection silently
        // killed by the server/NAT while the phone slept, where no network
        // change fired to evict the pool. Without it the next request reuses
        // that corpse and hangs forever — the "must kill and reopen the app"
        // symptom. With it the request fails after 30 s and (with OkHttp's
        // retryOnConnectionFailure) re-dials a fresh connection.
        //
        // Long-lived endpoints opt out: streaming calls override both timeouts
        // to infinite via disableStreamTimeouts(), the timeline raises them to
        // 180 s, and backups set their own 60 s socket timeout. This is safe on
        // iOS for the same reason — only the idle interval is bounded, and the
        // streams that would legitimately sit quiet for >30 s already opt out.
        install(HttpTimeout) {
            socketTimeoutMillis = 30_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("[Ktor] $message")
                }
            }
            level = LogLevel.INFO
        }
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
            attemptRefresh(client, baseUrlProvider(), tokenStorage)
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
