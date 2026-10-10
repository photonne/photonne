package com.photonne.app.ui.main

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarControllerTest {

    @Test
    fun trailingLambdaIsTheActionAndDoesNotRunWhenTheSnackbarExpires() = runTest {
        val hostState = SnackbarHostState()
        val controller = SnackbarController(
            hostState,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )
        var undone = false

        controller.show("1 foto movida a la papelera", "Deshacer") { undone = true }

        val data = hostState.currentSnackbarData!!
        assertEquals("Deshacer", data.visuals.actionLabel)
        assertEquals(SnackbarDuration.Long, data.visuals.duration)
        data.dismiss()
        assertFalse(undone)
    }

    @Test
    fun pressingTheActionRunsItAndSkipsOnDismissed() = runTest {
        val hostState = SnackbarHostState()
        val controller = SnackbarController(
            hostState,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )
        var undone = false
        var dismissed = false

        controller.show(
            message = "Carpeta quitada",
            actionLabel = "Deshacer",
            onDismissed = { dismissed = true }
        ) { undone = true }

        hostState.currentSnackbarData!!.performAction()
        assertTrue(undone)
        assertFalse(dismissed)
    }
}
