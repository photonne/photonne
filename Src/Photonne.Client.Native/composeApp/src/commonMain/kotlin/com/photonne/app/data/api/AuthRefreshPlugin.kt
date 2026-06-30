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
import kotlinx.coroutines.CancellationException
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
 * Outcome of a token refresh attempt. The distinction between [AuthRejected]
 * and [Transient] is what keeps a transient network blip — extremely common
 * mid network-switch, or when the refresh itself hits a public URL that is
 * unreachable from inside the LAN — from nuking a perfectly valid session.
 */
private enum class RefreshOutcome {
    /** Server issued new tokens. */
    Success,
    /** Server definitively rejected the refresh token (401/403): log out. */
    AuthRejected,
    /** Network error, timeout, or 5xx: inconclusive — keep the session. */
    Transient,
}

/**
 * Refresh-on-401 mirror of Photonne.Client.Web/Services/AuthRefreshHandler.cs.
 * Uses a mutex so concurrent 401s only trigger one refresh.
 */
fun buildPhotonneHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenStorage: TokenStorage,
    authState: AuthStateHolder,
    onConnectionError: (() -> Unit)? = null
): HttpClient = buildPhotonneHttpClient(engine, { baseUrl }, tokenStorage, authState, onConnectionError)

fun buildPhotonneHttpClient(
    engine: HttpClientEngine,
    baseUrlProvider: () -> String,
    tokenStorage: TokenStorage,
    authState: AuthStateHolder,
    onConnectionError: (() -> Unit)? = null
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
            // Bound the connect phase so a request to an unreachable host (the
            // classic case: the public URL is not reachable from inside the
            // home LAN without NAT hairpin) fails in 4 s instead of OkHttp's
            // 10 s default. This also caps how long a Transient refresh can
            // hold the refresh mutex below.
            connectTimeoutMillis = 4_000
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

        // A thrown exception here is a connection/timeout failure (a non-2xx
        // status returns normally and is handled below). Nudge the reachability
        // probe so a request that connect-timed-out against the public URL while
        // on the LAN can re-discover the local URL — then rethrow so the caller
        // still sees the error for this attempt.
        val firstCall = try {
            execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onConnectionError?.invoke()
            throw t
        }
        if (firstCall.response.status != HttpStatusCode.Unauthorized) return@intercept firstCall

        val outcome = refreshMutex.withLock {
            attemptRefresh(client, baseUrlProvider(), tokenStorage)
        }
        when (outcome) {
            RefreshOutcome.Success -> Unit // fall through to retry with the new token
            RefreshOutcome.AuthRejected -> {
                tokenStorage.clear()
                authState.update(AuthState.Unauthenticated)
                return@intercept firstCall
            }
            RefreshOutcome.Transient -> {
                // Network/timeout/5xx during refresh — inconclusive. Do NOT clear
                // a valid session; surface the original 401 and let the next
                // request retry once connectivity / the effective URL is correct.
                return@intercept firstCall
            }
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
): RefreshOutcome {
    // No refresh token to spend — nothing transient about it, so this is a
    // definitive auth failure.
    val refreshToken = tokenStorage.getRefreshToken() ?: return RefreshOutcome.AuthRejected
    val deviceId = tokenStorage.getDeviceId()
    return try {
        val response: HttpResponse = client.post("$baseUrl/api/auth/refresh") {
            skipAuthRefresh()
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken = refreshToken, deviceId = deviceId))
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val body: RefreshTokenResponse = response.body()
                tokenStorage.saveTokens(body.token, body.refreshToken)
                RefreshOutcome.Success
            }
            // The server explicitly rejected the refresh token (expired/revoked).
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> RefreshOutcome.AuthRejected
            // 5xx / 0 / anything else: the credential may still be valid, the
            // server or network is just having a moment. Keep the session.
            else -> RefreshOutcome.Transient
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // Any thrown exception (timeout, connection reset, DNS, TLS, even a
        // malformed 200 body) is treated as transient regardless of engine —
        // we never inspect the exception type, which keeps this engine-neutral
        // across OkHttp / Darwin / CIO.
        RefreshOutcome.Transient
    }
}
