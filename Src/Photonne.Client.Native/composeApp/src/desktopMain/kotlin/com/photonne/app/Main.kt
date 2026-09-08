package com.photonne.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import com.photonne.app.ui.navigation.DesktopBackDispatcher
import org.koin.core.context.startKoin

fun main() {
    val apiBaseUrl = (System.getProperty("photonne.api.baseUrl")
        ?: System.getenv("PHOTONNE_API_BASE_URL"))
        ?.takeIf { it.isNotBlank() }

    val useFakeMemories = (System.getProperty("photonne.fake.memories")
        ?: System.getenv("PHOTONNE_FAKE_MEMORIES"))
        ?.equals("true", ignoreCase = true) == true

    startKoin {
        modules(
            commonModule(
                PhotonneAppConfig(
                    apiBaseUrl = apiBaseUrl,
                    useFakeMemories = useFakeMemories
                )
            ),
            platformModule()
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(),
            title = "Photonne",
            // onKeyEvent (no preview): el campo con foco ve la tecla primero,
            // así Escape en un buscador no navega hacia atrás por sorpresa.
            onKeyEvent = ::handleBackKey
        ) {
            @OptIn(ExperimentalComposeUiApi::class)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Press) { event ->
                        if (event.buttons.isBackPressed) {
                            DesktopBackDispatcher.dispatch()
                        }
                    }
            ) {
                App()
            }
        }
    }
}

/** Escape y Alt+← replican el gesto de "atrás" que escritorio no tiene. */
private fun handleBackKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isBack = event.key == Key.Escape ||
        (event.key == Key.DirectionLeft && event.isAltPressed)
    return isBack && DesktopBackDispatcher.dispatch()
}
