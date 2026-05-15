package com.photonne.app.data.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop fallback: emits once at startup. We don't currently bridge to
 * java.net.NetworkInterface change notifications because the desktop target
 * is dev-only and rarely roams between networks.
 */
class DesktopNetworkMonitor : NetworkMonitor {
    override val changes: Flow<Unit> = flowOf(Unit)
}
