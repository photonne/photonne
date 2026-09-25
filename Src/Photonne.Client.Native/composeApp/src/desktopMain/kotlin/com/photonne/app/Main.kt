package com.photonne.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import com.photonne.app.resources.Res
import com.photonne.app.resources.photonne_app_icon
import com.photonne.app.ui.navigation.DesktopBackDispatcher
import java.awt.Dimension
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin

fun main() {
    val apiBaseUrl = (System.getProperty("photonne.api.baseUrl")
        ?: System.getenv("PHOTONNE_API_BASE_URL"))
        ?.takeIf { it.isNotBlank() }

    val useFakeMemories = (System.getProperty("photonne.fake.memories")
        ?: System.getenv("PHOTONNE_FAKE_MEMORIES"))
        ?.equals("true", ignoreCase = true) == true

    val httpLogging = (System.getProperty("photonne.http.log")
        ?: System.getenv("PHOTONNE_HTTP_LOG"))
        ?.equals("true", ignoreCase = true) == true

    startKoin {
        modules(
            commonModule(
                PhotonneAppConfig(
                    apiBaseUrl = apiBaseUrl,
                    useFakeMemories = useFakeMemories,
                    httpLogging = httpLogging,
                )
            ),
            platformModule()
        )
    }

    application {
        val windowState = rememberWindowState(
            placement = WindowGeometry.restorePlacement(),
            size = WindowGeometry.restoreSize(),
            position = WindowGeometry.restorePosition()
        )

        // Persistir la geometría según cambia (los writes a Preferences son
        // baratos y así un cierre forzado tampoco la pierde).
        LaunchedEffect(windowState) {
            snapshotFlow {
                Triple(windowState.size, windowState.position, windowState.placement)
            }
                .distinctUntilChanged()
                .collect { WindowGeometry.save(windowState) }
        }

        Window(
            onCloseRequest = {
                WindowGeometry.save(windowState)
                exitApplication()
            },
            state = windowState,
            title = "Photonne",
            icon = painterResource(Res.drawable.photonne_app_icon),
            // onKeyEvent (no preview): el campo con foco ve la tecla primero,
            // así Escape en un buscador no navega hacia atrás por sorpresa.
            onKeyEvent = ::handleBackKey
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
            }
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

private const val MIN_WINDOW_WIDTH = 960
private const val MIN_WINDOW_HEIGHT = 640

/** Escape y Alt+← replican el gesto de "atrás" que escritorio no tiene. */
private fun handleBackKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isBack = event.key == Key.Escape ||
        (event.key == Key.DirectionLeft && event.isAltPressed)
    return isBack && DesktopBackDispatcher.dispatch()
}

/**
 * Tamaño, posición y maximizado de la ventana entre arranques, en
 * java.util.prefs (geometría, nada sensible — las credenciales viven en el
 * almacén cifrado). Una posición fuera de rango tras cambiar de monitor la
 * corrige el propio SO al mostrar la ventana.
 */
private object WindowGeometry {
    private val node = Preferences.userRoot().node("com/photonne/app/window")

    fun restoreSize(): DpSize = DpSize(
        node.getFloat("width", 1280f).dp,
        node.getFloat("height", 800f).dp
    )

    fun restorePosition(): WindowPosition {
        val x = node.getFloat("x", Float.NaN)
        val y = node.getFloat("y", Float.NaN)
        if (x.isNaN() || y.isNaN()) return WindowPosition.PlatformDefault
        return WindowPosition(x.dp, y.dp)
    }

    fun restorePlacement(): WindowPlacement =
        if (node.getBoolean("maximized", false)) WindowPlacement.Maximized
        else WindowPlacement.Floating

    fun save(state: WindowState) {
        runCatching {
            node.putBoolean("maximized", state.placement == WindowPlacement.Maximized)
            // Solo la geometría flotante: la de un maximizado no es restaurable.
            if (state.placement == WindowPlacement.Floating) {
                node.putFloat("width", state.size.width.value)
                node.putFloat("height", state.size.height.value)
                val position = state.position
                if (position is WindowPosition.Absolute) {
                    node.putFloat("x", position.x.value)
                    node.putFloat("y", position.y.value)
                }
            }
            node.flush()
        }
    }
}
