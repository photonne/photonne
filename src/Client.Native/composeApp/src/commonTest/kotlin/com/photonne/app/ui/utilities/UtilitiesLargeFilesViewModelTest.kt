package com.photonne.app.ui.utilities

import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.utilities.UtilitiesRepository
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import com.photonne.app.ui.timelineItemJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UtilitiesLargeFilesViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a count picked while loading replaces the load in flight`() = vms.runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val neverAnswered = CompletableDeferred<Unit>()
        val counts = mutableListOf<String?>()
        val api = testApi { request ->
            val count = request.url.parameters["count"]
            counts += count
            if (count == "50") {
                firstStarted.complete(Unit)
                neverAnswered.await()
            }
            json("[${timelineItemJson("big-$count")}]")
        }
        val vm = vms.track(UtilitiesLargeFilesViewModel(
            UtilitiesRepository(api), testErrorFactory(api), AssetMutationBus()
        ))

        vm.ensureLoaded()
        firstStarted.await()
        vm.setCount(100)
        val state = vm.state.first { !it.isLoading }

        assertEquals(listOf<String?>("50", "100"), counts)
        assertEquals(100, state.count)
        assertEquals(listOf("big-100"), state.items.map { it.id })
    }
}
