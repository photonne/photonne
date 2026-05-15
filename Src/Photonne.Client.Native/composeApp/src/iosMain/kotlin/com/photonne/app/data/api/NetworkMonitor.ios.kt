package com.photonne.app.data.api

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {

    override val changes: Flow<Unit> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(
            monitor,
            dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        )
        nw_path_monitor_set_update_handler(monitor) { _ ->
            trySend(Unit)
        }
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .onStart { emit(Unit) }
}
