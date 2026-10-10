package com.photonne.app.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Escritorio no tiene notificaciones de cambio de red utilizables desde la JVM,
 * así que emite un tick periódico: es lo que consume LocalReachabilityProbe
 * para re-decidir URL local vs pública, y 30 s de latencia al cambiar de red
 * (portátil que va y viene de casa) es suficiente. Un HEAD cada 30 s a la URL
 * local es despreciable.
 */
class DesktopNetworkMonitor : NetworkMonitor {
    override val changes: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }
}
