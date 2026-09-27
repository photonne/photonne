package com.photonne.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemorySelectionUiState(
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

/**
 * Selección múltiple del detalle de un recuerdo.
 *
 * El recuerdo no tiene ViewModel propio: llega con sus fotos en mano
 * ([MemoryDetailContext]) y el host le quita lo borrado vía el bus. Este solo
 * guarda la selección y corre las acciones en bloque, con el mismo contrato
 * (isBulkMutating/error/clearError/bulkAddToAlbum) que el resto de pantallas
 * para que la barra de selección y el diálogo de añadir a álbum lo traten
 * igual. Papelera y archivar emiten `Removed` en el bus (lo hace el
 * repositorio) y así el recuerdo abierto pierde esas fotos.
 */
class MemorySelectionViewModel(
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(MemorySelectionUiState())
    val state: StateFlow<MemorySelectionUiState> = _state.asStateFlow()

    fun toggleSelection(assetId: String) {
        _state.update { it.copy(selection = it.selection.toggled(assetId)) }
    }

    /** Un frame de arrastre en banda, en una sola mutación — ver [SelectionPatch]. */
    fun applySelection(patch: SelectionPatch) {
        if (patch.isEmpty) return
        _state.update { it.copy(selection = it.selection.applying(patch)) }
    }

    fun toggleSelectAll(allIds: List<String>) {
        _state.update { it.copy(selection = it.selection.toggledAll(allIds)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun bulkArchive(onResult: (UiError?) -> Unit = {}) =
        runBulk(ErrorMessages.ARCHIVE_FAILED, onResult) { assetRepository.archive(it) }

    fun bulkTrash(onResult: (UiError?) -> Unit = {}) =
        runBulk(ErrorMessages.TRASH_FAILED, onResult) { assetRepository.trash(it) }

    fun bulkAddToAlbum(
        albumId: String,
        items: List<TimelineItem>,
        onSuccess: (List<TimelineItem>) -> Unit = {},
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val added = items.filter { it.id in ids }
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onSuccess(added)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    private fun runBulk(
        errorFallback: String,
        onResult: (UiError?) -> Unit,
        action: suspend (List<String>) -> Unit,
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null, selection = emptySet()) }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onResult(null)
                }
                .onFailure { error ->
                    val uiError = errorFactory.from(error, errorFallback)
                    _state.update { it.copy(isBulkMutating = false, error = uiError) }
                    onResult(uiError)
                }
        }
    }
}
