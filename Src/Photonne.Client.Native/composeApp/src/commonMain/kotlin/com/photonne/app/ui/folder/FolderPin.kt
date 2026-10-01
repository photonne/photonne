package com.photonne.app.ui.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.Res
import com.photonne.app.resources.folder_pin_failed
import com.photonne.app.resources.folder_unpin_failed
import com.photonne.app.ui.main.LocalSnackbarController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/*
 * Fijar una carpeta, el gemelo de AlbumPin.kt: personal, optimista y siempre a
 * través de [FoldersViewModel], que es quien tiene la lista completa (también
 * las carpetas anidadas) y la sección "Fijados" de Colecciones.
 */

/** Acción de fijar/desfijar por id de carpeta, con el snackbar de fallo ya resuelto. */
@Composable
fun rememberFolderPinToggle(viewModel: FoldersViewModel = koinViewModel()): (String) -> Unit {
    val snackbar = LocalSnackbarController.current
    val pinFailed = stringResource(Res.string.folder_pin_failed)
    val unpinFailed = stringResource(Res.string.folder_unpin_failed)
    return remember(viewModel, snackbar, pinFailed, unpinFailed) {
        { folderId ->
            viewModel.togglePin(folderId) { pinning ->
                snackbar?.show(if (pinning) pinFailed else unpinFailed)
            }
        }
    }
}

/**
 * Si [folderId] está fijada según la lista de carpetas (que es la que cambia
 * al fijar); [fallback] si la carpeta no está en ella.
 */
@Composable
fun rememberFolderPinned(
    folderId: String,
    fallback: Boolean,
    viewModel: FoldersViewModel = koinViewModel()
): Boolean {
    val flow = remember(viewModel, folderId) {
        viewModel.state
            .map { state -> state.findFolder(folderId)?.isPinned }
            .distinctUntilChanged()
    }
    val pinned by flow.collectAsStateWithLifecycle(
        initialValue = viewModel.state.value.findFolder(folderId)?.isPinned
    )
    return pinned ?: fallback
}
