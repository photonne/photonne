package com.photonne.app.ui.library

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import com.photonne.app.ui.timelineItemJson
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideosViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loads_the_videos_and_a_heart_only_marks_them() = vms.runTest {
        val paths = mutableListOf<String>()
        val api = testApi { request ->
            paths += request.url.encodedPath
            json("""{"items":[${timelineItemJson("v1")},${timelineItemJson("v2")}],"hasMore":false}""")
        }
        val bus = AssetMutationBus()
        val vm = vms.track(VideosViewModel(
            assetRepository = AssetDetailRepository(api),
            albumsRepository = AlbumsRepository(api),
            errorFactory = testErrorFactory(api),
            mutationBus = bus,
        ))

        vm.ensureLoaded()
        val loaded = vm.state.first { it.loaded }
        assertEquals(listOf("v1", "v2"), loaded.items.map { it.id })
        assertEquals(listOf("/api/assets/videos"), paths)

        bus.emit(AssetMutation.FavoriteChanged("v2", true))
        val hearted = vm.state.first { s -> s.items.any { it.id == "v2" && it.isFavorite } }
        assertEquals(listOf("v1", "v2"), hearted.items.map { it.id })

        bus.emit(AssetMutation.FavoriteChanged("v2", false))
        val unhearted = vm.state.first { s -> s.items.none { it.isFavorite } }
        assertEquals(listOf("v1", "v2"), unhearted.items.map { it.id })
        assertTrue(paths.size == 1, "a heart doesn't reload the list")
    }
}
