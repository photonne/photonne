package com.photonne.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_discard_confirm
import com.photonne.app.resources.admin_settings_discard_message
import com.photonne.app.resources.admin_settings_discard_title
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.contentWidth
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * Andamio de toda subpantalla que es una columna con scroll y no una lista:
 * los formularios de Cuenta (Perfil, Seguridad, Conexión, Apariencia), el
 * índice de Ajustes, los hubs y editores de admin. Cada una pegaba las mismas
 * cuarenta líneas — Box, haze, estado de scroll, el padding que libra el cromo
 * flotante y la nav flotante, y el cromo cableado a ese scroll — con márgenes
 * que variaban de copia en copia.
 *
 * [body] recibe una función `page` que pinta la columna con padding y scroll,
 * para que quien llama pueda pintar otra cosa en su lugar (un spinner, un
 * vacío) sin perder el cromo.
 *
 * Con [hasUnsavedChanges], salir (flecha de la cápsula o gesto del sistema)
 * pide confirmación antes de descartar lo editado.
 */
@Composable
fun FormPageScaffold(
    title: String,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    hasUnsavedChanges: Boolean = false,
    body: @Composable (page: @Composable (@Composable ColumnScope.() -> Unit) -> Unit) -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()

    var confirmDiscard by remember { mutableStateOf(false) }
    val guardedBack = { if (hasUnsavedChanges) confirmDiscard = true else onBack() }
    PlatformBackHandler(enabled = hasUnsavedChanges) { confirmDiscard = true }

    Box(modifier = Modifier.fillMaxSize()) {
        body { content ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .hazeSource(hazeState)
                    .contentWidth()
                    .padding(
                        start = Spacing.screenHorizontal,
                        end = Spacing.screenHorizontal,
                        top = Spacing.lg + subscreenChromeReservedTop(),
                        bottom = Spacing.lg + floatingNavBarReservedHeight()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content
            )
        }
        SubscreenFloatingChrome(
            title = title,
            onBack = guardedBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_settings_discard_title),
            message = stringResource(Res.string.admin_settings_discard_message),
            confirmLabel = stringResource(Res.string.admin_settings_discard_confirm),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            }
        )
    }
}
