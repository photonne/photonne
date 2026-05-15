package com.photonne.app.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private val PROBE_TIMEOUT = 800.milliseconds

/**
 * Decides whether to use the LAN URL or the public URL by HEAD-ing the local
 * URL on every network change. Result is flipped into [ServerUrlStore]; the
 * shared HttpClient reads the effective URL at request time so requests
 * already in flight aren't affected.
 *
 * No platform permissions are required: we never read the SSID — if the LAN
 * URL responds within [PROBE_TIMEOUT] we treat ourselves as "home", otherwise
 * we fall back to public.
 */
class LocalReachabilityProbe(
    private val httpClient: HttpClient,
    private val store: ServerUrlStore,
    private val networkMonitor: NetworkMonitor
) {

    fun start(scope: CoroutineScope) {
        // Each network tick (WiFi on/off, capabilities changed, VPN flipped)
        // reruns the probe. We do NOT key this by localUrl — that would let
        // distinctUntilChanged swallow repeated ticks while the URL stays the
        // same, which is exactly the case where WiFi cycles.
        scope.launch {
            networkMonitor.changes.collectLatest { runProbe() }
        }
        // A separate subscription handles the user editing the local URL in
        // settings: we drop the initial value because the network-change flow
        // above will already have probed it once on startup.
        scope.launch {
            store.localUrl.drop(1).collectLatest { runProbe() }
        }
    }

    suspend fun runProbe(): Boolean {
        val url = store.getLocal()
        if (url.isNullOrEmpty()) {
            store.setLocalReachable(false)
            return false
        }
        val ok = try {
            withTimeoutOrNull(PROBE_TIMEOUT) {
                val response: HttpResponse = httpClient.head("$url/api/auth/login") {
                    skipAuthRefresh()
                }
                response.status.value < 500
            } ?: false
        } catch (e: CancellationException) {
            // Coroutine was cancelled by collectLatest because a newer tick
            // arrived (or the scope died). Propagate so the parent learns of
            // it — DON'T flip the flag, otherwise we'd briefly mark the LAN
            // unreachable just because we got interrupted.
            throw e
        } catch (t: Throwable) {
            // Anything else (IOException, CleartextNotPermitted, DNS, …)
            // means the LAN URL is unreachable from this network.
            println("[Photonne] Local reachability probe failed: ${t::class.simpleName}: ${t.message}")
            false
        }
        store.setLocalReachable(ok)
        return ok
    }
}
