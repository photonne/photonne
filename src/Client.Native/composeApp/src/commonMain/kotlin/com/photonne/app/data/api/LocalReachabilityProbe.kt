package com.photonne.app.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

// A LAN device's first TCP+TLS handshake routinely needs more than the old
// 800 ms right after a network change (ARP/DHCP settling, TLS). Three attempts
// with this budget make a single slow handshake stop locking the app onto the
// public URL for the whole session.
private val PROBE_TIMEOUT = 2_500.milliseconds
private const val PROBE_ATTEMPTS = 3
private val PROBE_RETRY_DELAY = 200.milliseconds

// Coalesces the bursts of failed requests that arrive together (a timeline page
// + its thumbnails all connect-timing-out at once) into a single re-probe.
private val REPROBE_DEBOUNCE = 1_500.milliseconds

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

    // Re-probe requests coming from request-time connection failures (see
    // [requestReprobe]). Debounced + collectLatest so a burst of failures
    // triggers at most one extra probe.
    private val reprobeRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

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
        // Self-heal: when a live request fails to connect while we're stuck on
        // the public URL, re-probe so we can flip back to the LAN without
        // waiting for a network-change event that may never come.
        scope.launch {
            // Manual debounce: collectLatest cancels the delay (and any probe
            // in flight) when a newer request arrives, so a burst of failures
            // collapses into a single probe. Avoids the @FlowPreview debounce
            // operator the project doesn't opt into.
            reprobeRequests.collectLatest {
                delay(REPROBE_DEBOUNCE)
                runProbe()
            }
        }
    }

    /**
     * Ask for a re-probe after a request failed to connect. No-op unless a
     * local URL is configured and we are currently NOT using it — i.e. exactly
     * the "stuck on public from inside the LAN" case we want to recover from.
     * Cheap and non-blocking; the actual probe is debounced in [start].
     */
    fun requestReprobe() {
        if (store.isLocalReachable()) return
        if (store.getLocal().isNullOrEmpty()) return
        reprobeRequests.tryEmit(Unit)
    }

    suspend fun runProbe(): Boolean {
        val url = store.getLocal()
        if (url.isNullOrEmpty()) {
            store.setLocalReachable(false)
            return false
        }
        // Retry a few times before concluding "not home": a single slow first
        // handshake right after a network change must not lock us onto public.
        repeat(PROBE_ATTEMPTS) { attempt ->
            val ok = try {
                withTimeoutOrNull(PROBE_TIMEOUT) {
                    val response: HttpResponse = httpClient.head("$url/api/auth/login") {
                        skipAuthRefresh()
                    }
                    response.status.value < 500
                } ?: false
            } catch (e: CancellationException) {
                // Cancelled by collectLatest because a newer tick arrived (or
                // the scope died). Propagate — DON'T flip the flag, otherwise
                // we'd briefly mark the LAN unreachable just from an interrupt.
                throw e
            } catch (t: Throwable) {
                // Anything else (IOException, CleartextNotPermitted, DNS, …)
                // is a miss; retry until we run out of attempts.
                println("[Photonne] Local reachability probe miss ${attempt + 1}: ${t::class.simpleName}: ${t.message}")
                false
            }
            if (ok) {
                store.setLocalReachable(true)
                return true
            }
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_DELAY)
        }
        store.setLocalReachable(false)
        return false
    }
}
