package com.photonne.app.data.realtime

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Listens to the server's event stream (`GET /api/events`) while the app is
 * in the foreground, so photos that arrive from another device or the
 * indexer show up without pulling to refresh. "library" reloads every list
 * through [AssetMutation.AllChanged] (at most every [LIBRARY_EVERY_MS], as an
 * import says it every second); "notifications" calls [onNotifications].
 * Reconnects with a growing wait; after a reconnect the lists reload, since
 * changes made meanwhile weren't heard.
 */
class ServerEventsListener(
    private val api: PhotonneApi,
    private val mutationBus: AssetMutationBus,
) {
    /** Runs until cancelled (the app goes to the background or signs out). */
    suspend fun run(onNotifications: () -> Unit) = coroutineScope {
        var lastLibrary: TimeSource.Monotonic.ValueTimeMark? = null
        var pendingLibrary: Job? = null

        fun reloadLibrary() {
            if (pendingLibrary?.isActive == true) return
            val waitMs = lastLibrary?.let { (LIBRARY_EVERY_MS - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0) } ?: 0
            pendingLibrary = launch {
                delay(waitMs)
                lastLibrary = TimeSource.Monotonic.markNow()
                mutationBus.emit(AssetMutation.AllChanged)
            }
        }

        var attempt = 0
        while (true) {
            try {
                var connected = false
                api.serverEvents().collect { event ->
                    if (!connected) {
                        connected = true
                        if (attempt > 0) reloadLibrary()
                        attempt = 0
                    }
                    when (event) {
                        "library" -> reloadLibrary()
                        "notifications" -> onNotifications()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // No network, server restarting, an older server (404): retry below.
            }
            delay(backoffMs(attempt++))
        }
    }

    companion object {
        const val LIBRARY_EVERY_MS = 10_000L

        /** 2 s, 4 s, 8 s… up to a minute: an older server without the stream costs one request a minute. */
        fun backoffMs(attempt: Int): Long = minOf(60_000L, 2_000L shl minOf(attempt, 5))
    }
}
