package com.photonne.app.ui.search

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.search.PinnedSearchFilter
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.data.search.RecentSearchStore
import com.russhwolf.settings.MapSettings
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import com.photonne.app.ui.timelineItemJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val vms = TrackedViewModels()
    private val recents = RecentSearchStore(MapSettings())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): SearchViewModel {
        val api = testApi(handler)
        return vms.track(SearchViewModel(
            repository = SearchRepository(api),
            assetRepository = AssetDetailRepository(api),
            albumsRepository = AlbumsRepository(api),
            errorFactory = testErrorFactory(api),
            mutationBus = AssetMutationBus(),
            recentSearches = recents,
        ))
    }

    private suspend fun SearchViewModel.settled(): SearchUiState =
        state.first { !it.isLoading && !it.isAppending }

    @Test
    fun a_failed_search_drops_the_results_of_the_previous_one() = vms.runTest {
        var fail = false
        val vm = viewModel {
            if (fail) respond("", HttpStatusCode.InternalServerError)
            else json("""{"items":[${timelineItemJson("a")}],"hasMore":true}""")
        }
        vm.toggleObjectLabel("dog")
        assertEquals(listOf("a"), vm.settled().results.map { it.id })

        fail = true
        vm.toggleObjectLabel("cat")
        val state = vm.settled()

        assertTrue(state.results.isEmpty(), "results of «dog» must not answer «cat»")
        assertFalse(state.hasMore)
        assertNotNull(state.error)
    }

    @Test
    fun the_media_type_alone_searches_and_goes_to_the_server() = vms.runTest {
        val types = mutableListOf<String?>()
        val vm = viewModel { request ->
            types += request.url.parameters["type"]
            json("""{"items":[${timelineItemJson("v")}],"hasMore":false}""")
        }
        vm.setMediaType(SearchMediaType.Video)
        val state = vm.settled()

        assertEquals(1, state.activeFilterCount)
        assertEquals(listOf("v"), state.results.map { it.id })
        assertEquals(listOf<String?>("video"), types)

        vm.clearAll()
        assertEquals(null, vm.state.value.mediaType)
    }

    @Test
    fun a_detail_tapped_in_the_viewer_searches_on_its_own_until_its_chip_goes() = vms.runTest {
        val queries = mutableListOf<Map<String, String?>>()
        val vm = viewModel { request ->
            val p = request.url.parameters
            queries += mapOf(
                "tag" to p["tag"],
                "cameraMake" to p["cameraMake"],
                "cameraModel" to p["cameraModel"],
                "placeId" to p["placeId"]
            )
            json("""{"items":[${timelineItemJson("x")}],"hasMore":false}""")
        }
        vm.toggleObjectLabel("dog")
        vm.settled()

        vm.showResultsFor(PinnedSearchFilter.Camera("Apple", "iPhone 15"))
        val state = vm.settled()

        // It replaces what was there: only the camera narrows the search.
        assertTrue(state.selectedObjectLabels.isEmpty())
        assertEquals(1, state.activeFilterCount)
        assertEquals(
            mapOf("tag" to null, "cameraMake" to "Apple", "cameraModel" to "iPhone 15", "placeId" to null),
            queries.last()
        )

        vm.showResultsFor(PinnedSearchFilter.Place("p-1", "Madrid"))
        vm.settled()
        assertEquals("p-1", queries.last()["placeId"])

        vm.clearPinned()
        assertEquals(null, vm.state.value.pinned)
    }

    @Test
    fun ai_search_unavailable_falls_back_to_text_and_says_so() = vms.runTest {
        val paths = mutableListOf<String>()
        val vm = viewModel { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("/semantic")) {
                json(
                    """{"error":"Semantic search unavailable","code":"semantic_search_unavailable"}""",
                    HttpStatusCode.ServiceUnavailable
                )
            } else json("""{"items":[${timelineItemJson("t")}],"hasMore":false}""")
        }
        vm.setMode(SearchMode.Semantic)
        vm.setQuery("playa")
        val state = vm.state.first { it.semanticUnavailable && !it.isLoading }

        assertEquals(SearchMode.Text, state.mode)
        assertEquals(listOf("t"), state.results.map { it.id })
        assertEquals(null, state.error, "the fallback is not an error")
        assertEquals(listOf("/api/assets/search/semantic", "/api/assets/search"), paths)

        vm.consumeSemanticUnavailable()
        assertFalse(vm.state.value.semanticUnavailable)
    }

    @Test
    fun other_ai_search_errors_stay_errors() = vms.runTest {
        val vm = viewModel { respond("", HttpStatusCode.ServiceUnavailable) }
        vm.setMode(SearchMode.Semantic)
        vm.setQuery("playa")
        val state = vm.state.first { it.error != null }

        assertEquals(SearchMode.Semantic, state.mode)
        assertFalse(state.semanticUnavailable)
    }

    @Test
    fun `a failed next page is an append error - keeps the results and retries`() = vms.runTest {
        var call = 0
        val vm = viewModel {
            call++
            when (call) {
                1 -> json("""{"items":[${timelineItemJson("a")}],"hasMore":true}""")
                2 -> respond("", HttpStatusCode.InternalServerError)
                else -> json("""{"items":[${timelineItemJson("b")}],"hasMore":false}""")
            }
        }
        vm.toggleObjectLabel("dog")
        vm.settled()

        vm.loadMore()
        val failed = vm.settled()
        assertEquals(listOf("a"), failed.results.map { it.id })
        assertNotNull(failed.appendError)
        assertEquals(null, failed.error, "must not leak into the add-to-album dialog")

        vm.loadMore()
        val retried = vm.state.first { !it.isAppending && it.results.size == 2 }
        assertEquals(listOf("a", "b"), retried.results.map { it.id })
        assertEquals(null, retried.appendError)
    }
}
