package com.photonne.app.data.api

import kotlinx.coroutines.flow.Flow

/**
 * Emits a tick whenever the device's network connectivity changes (network
 * available, lost, or capabilities updated). [LocalReachabilityProbe]
 * subscribes to this to re-probe the LAN URL every time we might have moved
 * between WiFi and cellular/VPN.
 *
 * Implementations must emit one initial tick on subscribe so the probe runs
 * at app startup.
 */
interface NetworkMonitor {
    val changes: Flow<Unit>

    /**
     * Si el dispositivo tiene ahora mismo una red con salida (no si el
     * servidor responde: eso lo decide [ConnectivityMonitor]). Por defecto
     * true donde no hay forma fiable de saberlo (escritorio).
     */
    fun isNetworkAvailable(): Boolean = true
}
