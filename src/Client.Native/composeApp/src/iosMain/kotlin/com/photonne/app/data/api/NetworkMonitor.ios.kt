package com.photonne.app.data.api

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {

    // Último estado que dio el monitor. La sonda de reachability mantiene
    // suscrito [changes] mientras vive la app, así que está al día; hasta el
    // primer aviso se supone que hay red.
    @kotlin.concurrent.Volatile
    private var satisfied: Boolean = true

    override fun isNetworkAvailable(): Boolean = satisfied

    override val changes: Flow<Unit> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(
            monitor,
            dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        )
        nw_path_monitor_set_update_handler(monitor) { path ->
            satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            trySend(Unit)
        }
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .onStart { emit(Unit) }
}
