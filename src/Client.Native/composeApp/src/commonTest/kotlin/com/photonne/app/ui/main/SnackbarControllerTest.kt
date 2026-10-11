package com.photonne.app.ui.main

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarControllerTest {

    @Test
    fun an_informative_message_waits_behind_a_visible_undo() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        val controller = SnackbarController(host, backgroundScope)
        var consolidated = false

        controller.show("Movida a la papelera", "Deshacer", onDismissed = { consolidated = true }) {}
        controller.show("Ajustes guardados")

        // The undo stays up (and its deletion is not consolidated yet).
        assertEquals("Movida a la papelera", host.currentSnackbarData?.visuals?.message)
        assertFalse(consolidated)

        host.currentSnackbarData?.dismiss()

        assertEquals("Ajustes guardados", host.currentSnackbarData?.visuals?.message)
        assertEquals(true, consolidated)
    }

    @Test
    fun a_new_message_with_action_replaces_the_visible_one() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        val controller = SnackbarController(host, backgroundScope)

        controller.show("Movida a la papelera", "Deshacer") {}
        controller.show("Archivada", "Deshacer") {}

        assertEquals("Archivada", host.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun a_new_informative_message_replaces_another_informative_one() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        val controller = SnackbarController(host, backgroundScope)

        controller.show("Guardado")
        controller.show("Copiado")

        assertEquals("Copiado", host.currentSnackbarData?.visuals?.message)
    }
}
