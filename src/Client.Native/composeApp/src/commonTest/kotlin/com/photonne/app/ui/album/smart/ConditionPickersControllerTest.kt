package com.photonne.app.ui.album.smart

import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A failed load in a condition picker must stay an error — not an empty list
 * marked as loaded — so the sheet offers Reintentar instead of "no hay…" and a
 * later call asks the server again.
 */
class ConditionPickersControllerTest {

    private var fail = true
    private var requests = 0

    private fun TestScope.controller(okBody: (path: String) -> String): ConditionPickersController {
        val api = testApi { request ->
            requests++
            if (fail) respond("", HttpStatusCode.InternalServerError)
            else json(okBody(request.url.encodedPath))
        }
        return ConditionPickersController(
            SearchRepository(api),
            FoldersRepository(api),
            AuthStateHolder(),
            testErrorFactory(api),
            backgroundScope,
        )
    }

    @Test
    fun `a failed folder load is an error - not an empty loaded list - and retries`() = runTest {
        val pickers = controller {
            """[{"id":"f1","path":"/u/Viajes","name":"Viajes","createdAt":"2026-01-01T00:00:00Z"}]"""
        }

        pickers.ensureFolders()
        val failed = pickers.pickers.first { !it.foldersLoading && it.foldersError != null }
        assertFalse(failed.foldersLoaded)
        assertTrue(failed.folders.isEmpty())

        fail = false
        pickers.ensureFolders()
        val loaded = pickers.pickers.first { it.foldersLoaded }
        assertNull(loaded.foldersError)
        assertEquals(listOf("Viajes"), loaded.folders.map { it.name })
        assertEquals(2, requests)
    }

    @Test
    fun `a failed people search keeps its error and is not marked loaded`() = runTest {
        val pickers = controller { """{"total":1,"items":[{"id":"p1","name":"Ana"}]}""" }

        pickers.setPeopleQuery("an")
        val failed = pickers.pickers.first { !it.people.isLoading && it.people.error != null }.people
        assertFalse(failed.loadedOnce)
        assertEquals("an", failed.query)

        fail = false
        pickers.setPeopleQuery(failed.query)
        val loaded = pickers.pickers.first { it.people.loadedOnce }.people
        assertNull(loaded.error)
        assertEquals(listOf("Ana"), loaded.results.map { it.name })
    }

    @Test
    fun `failing scenes and objects report an error each`() = runTest {
        val pickers = controller { """[{"label":"beach","assetCount":5}]""" }

        pickers.setSceneQuery("")
        pickers.setObjectQuery("")
        val failed = pickers.pickers.first {
            !it.scenes.isLoading && !it.objects.isLoading && it.scenes.error != null && it.objects.error != null
        }
        assertFalse(failed.scenes.loadedOnce)
        assertFalse(failed.objects.loadedOnce)

        fail = false
        pickers.setSceneQuery("")
        val loaded = pickers.pickers.first { it.scenes.loadedOnce }
        assertNull(loaded.scenes.error)
        assertEquals(listOf("beach"), loaded.scenes.results.map { it.label })
        // Only the picker that was retried recovers.
        assertNotNull(loaded.objects.error)
    }
}
