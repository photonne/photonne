package com.photonne.app.ui.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_pin_failed
import com.photonne.app.resources.album_unpin_failed
import com.photonne.app.ui.main.LocalSnackbarController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/*
 * Fijar un álbum arriba de la lista. Es personal (no cambia nada para los
 * demás miembros) y optimista: la tarjeta cambia al instante y, si el
 * servidor falla, vuelve atrás con un snackbar. Todo pasa por
 * [AlbumsViewModel] para que la lista, el detalle y la cápsula de selección
 * vean el mismo estado; aquí vive el cableado para no engordar App.kt.
 */

/**
 * Acción de fijar/desfijar por id de álbum, con el snackbar de fallo ya
 * resuelto.
 */
@Composable
fun rememberAlbumPinToggle(viewModel: AlbumsViewModel = koinViewModel()): (String) -> Unit {
    val snackbar = LocalSnackbarController.current
    val pinFailed = stringResource(Res.string.album_pin_failed)
    val unpinFailed = stringResource(Res.string.album_unpin_failed)
    return remember(viewModel, snackbar, pinFailed, unpinFailed) {
        { albumId ->
            viewModel.togglePin(albumId) { pinning ->
                snackbar?.show(if (pinning) pinFailed else unpinFailed)
            }
        }
    }
}

/**
 * Si [albumId] está fijado según la lista de álbumes (que es la que cambia
 * al fijar); [fallback] si el álbum no está en ella.
 */
@Composable
fun rememberAlbumPinned(
    albumId: String,
    fallback: Boolean,
    viewModel: AlbumsViewModel = koinViewModel()
): Boolean {
    val flow = remember(viewModel, albumId) {
        viewModel.state
            .map { state -> state.albums.firstOrNull { it.id == albumId }?.isPinned }
            .distinctUntilChanged()
    }
    val pinned by flow.collectAsStateWithLifecycle(
        initialValue = viewModel.state.value.albums.firstOrNull { it.id == albumId }?.isPinned
    )
    return pinned ?: fallback
}
