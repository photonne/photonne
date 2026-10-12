package com.photonne.app.ui.explore

import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.respond
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreFacetsViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `objects failing do not take the scenes down with them`() = vms.runTest {
        var objectsFail = true
        var requests = 0
        val api = testApi { request ->
            requests++
            if (request.url.encodedPath.startsWith("/api/objects")) {
                if (objectsFail) respond("", HttpStatusCode.InternalServerError)
                else json("""[{"label":"dog","assetCount":3}]""")
            } else json("""[{"label":"beach","assetCount":5}]""")
        }
        val vm = vms.track(ExploreFacetsViewModel(SearchRepository(api), testErrorFactory(api), MapSettings()))

        vm.ensureLoaded()
        val failed = vm.state.first { it.attempted && !it.isLoading }
        assertEquals(listOf("beach"), failed.scenes.map { it.label })
        assertNull(failed.scenesError)
        assertNotNull(failed.objectsError)
        assertTrue(failed.objects.isEmpty())

        // A failed half counts as not loaded: coming back retries.
        objectsFail = false
        vm.ensureLoaded()
        val retried = vm.state.first { !it.isLoading }
        assertEquals(4, requests)
        assertEquals(listOf("dog"), retried.objects.map { it.label })
        assertNull(retried.objectsError)
    }
}
