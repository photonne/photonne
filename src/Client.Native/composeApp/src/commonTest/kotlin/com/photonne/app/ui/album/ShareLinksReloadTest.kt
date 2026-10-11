package com.photonne.app.ui.album

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
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

/**
 * "Mis enlaces" and an album's share sheet show the same links from two view
 * models that never talk to each other: each open must ask the server again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareLinksReloadTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun link(token: String) =
        """{"token":"$token","createdAt":"2026-01-01T00:00:00Z"}"""

    @Test
    fun my_links_reloads_on_every_open() = runTest {
        var links = listOf("a", "b")
        var requests = 0
        val api = testApi {
            requests++
            json(links.joinToString(",", "[", "]") { link(it) })
        }
        val vm = SentSharesViewModel(AlbumsRepository(api), testErrorFactory(api))

        vm.load()
        assertEquals(listOf("a", "b"), vm.state.first { it.hasLoaded && !it.isLoading }.links.map { it.token })

        // Revoked from the album's share sheet meanwhile.
        links = listOf("a")
        vm.load()
        val state = vm.state.first { !it.isLoading }

        assertEquals(2, requests)
        assertEquals(listOf("a"), state.links.map { it.token })
        assertFalse(state.isRefreshing, "an open reloads quietly")
    }

    @Test
    fun album_shares_reload_when_the_same_album_is_reopened() = runTest {
        var links = listOf("a", "b")
        var requests = 0
        val api = testApi {
            requests++
            json(links.joinToString(",", "[", "]") { link(it) })
        }
        val vm = AlbumSharesViewModel(AlbumsRepository(api), testErrorFactory(api))

        vm.open("album-1")
        assertEquals(2, vm.state.first { !it.isLoading }.links.size)

        // Revoked from "Mis enlaces" meanwhile.
        links = listOf("b")
        vm.open("album-1")
        val state = vm.state.first { !it.isLoading }

        assertEquals(2, requests)
        assertEquals(listOf("b"), state.links.map { it.token })
    }
}
