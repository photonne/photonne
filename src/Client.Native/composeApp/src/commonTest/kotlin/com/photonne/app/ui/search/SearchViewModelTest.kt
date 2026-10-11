package com.photonne.app.ui.search

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.search.SearchRepository
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
import kotlinx.coroutines.test.runTest
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): SearchViewModel {
        val api = testApi(handler)
        return SearchViewModel(
            repository = SearchRepository(api),
            assetRepository = AssetDetailRepository(api),
            albumsRepository = AlbumsRepository(api),
            errorFactory = testErrorFactory(api),
            mutationBus = AssetMutationBus(),
        )
    }

    private suspend fun SearchViewModel.settled(): SearchUiState =
        state.first { !it.isLoading && !it.isAppending }

    @Test
    fun a_failed_search_drops_the_results_of_the_previous_one() = runTest {
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
}
