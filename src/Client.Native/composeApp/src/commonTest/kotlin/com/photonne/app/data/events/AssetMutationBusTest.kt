package com.photonne.app.data.events

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetMutationBusTest {

    @Test
    fun a_burst_larger_than_any_fixed_buffer_reaches_a_busy_collector_whole() = runTest {
        val bus = AssetMutationBus()
        val received = mutableListOf<AssetMutation>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { received += it }
        }

        // Emitted synchronously, as a bulk favorite does: the collector can't
        // run until the loop ends.
        repeat(200) { bus.emit(AssetMutation.FavoriteChanged("asset-$it", isFavorite = true)) }
        advanceUntilIdle()

        assertEquals(200, received.size)
        collector.cancel()
    }
}
