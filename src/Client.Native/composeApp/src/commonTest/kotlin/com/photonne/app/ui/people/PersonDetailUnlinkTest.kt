package com.photonne.app.ui.people

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.data.timeline.MemoriesRepository
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailUnlinkTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): PersonDetailViewModel {
        val api = testApi(handler)
        return PersonDetailViewModel(
            peopleRepository = PeopleRepository(api),
            assetRepository = AssetDetailRepository(api),
            albumsRepository = AlbumsRepository(api),
            errorFactory = testErrorFactory(api),
            mutationBus = AssetMutationBus(),
            memoriesRepository = MemoriesRepository(api, PhotonneAppConfig(apiBaseUrl = "http://test.local")),
        )
    }

    private fun page(vararg ids: String) = ids.joinToString(",", """{"total":${ids.size},"items":[""", "]}") {
        """{"id":"$it","fileCreatedAt":"2026-01-01T00:00:00Z"}"""
    }

    /** Serves the person's first page and memories; [unlink] answers each unlink. */
    private fun handler(
        vararg ids: String,
        unlink: MockRequestHandleScope.(assetId: String) -> HttpResponseData,
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/unlink") -> unlink(path.split("/").dropLast(1).last())
            path.startsWith("/api/memories") -> json("[]")
            else -> json(page(*ids))
        }
    }

    private suspend fun PersonDetailViewModel.openLoaded(personId: String) {
        open(personId, "Marc")
        state.first { !it.isInitialLoading }
    }

    @Test
    fun `a partial unlink brings back only the photos that failed`() = runTest {
        val vm = viewModel(handler("a", "b", "c") { assetId ->
            if (assetId == "b") respond("", HttpStatusCode.InternalServerError)
            else json("""{"facesDetached":1}""")
        })
        vm.openLoaded("p-1")
        vm.setSelected(listOf("a", "b", "c"), selected = true)

        val done = CompletableDeferred<UnlinkOutcome>()
        vm.bulkUnlinkFromPerson { done.complete(it) }
        val outcome = done.await()
        val state = vm.state.value

        assertEquals(listOf("b"), state.items.map { it.id })
        assertEquals(1, state.total)
        assertEquals(2, outcome.unlinked)
        assertEquals(1, outcome.failed)
        assertEquals(2, outcome.facesDetached)
        assertNotNull(outcome.error)
        assertFalse(outcome.personEmptied)
        assertNull(state.error, "the caller shows it - state.error is painted only over an empty grid")
    }

    @Test
    fun `unlinking every photo reports the person as emptied`() = runTest {
        val vm = viewModel(handler("a", "b") { json("""{"facesDetached":1}""") })
        vm.openLoaded("p-1")
        vm.setSelected(listOf("a", "b"), selected = true)

        val done = CompletableDeferred<UnlinkOutcome>()
        vm.bulkUnlinkFromPerson { done.complete(it) }
        val outcome = done.await()

        assertTrue(outcome.personEmptied)
        assertEquals(0, outcome.remaining)
        assertTrue(vm.state.value.items.isEmpty())
    }
}
