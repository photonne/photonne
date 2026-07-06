package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem
import com.russhwolf.settings.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class FolderDetailUiState(
    val folderId: String? = null,
    val folderName: String? = null,
    val parentFolderId: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val subFolders: List<FolderSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false,
    val selectedSubfolderId: String? = null
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()

    val isSubfolderSelectionActive: Boolean get() = selectedSubfolderId != null

    val selectedSubfolder: FolderSummary?
        get() = selectedSubfolderId?.let { id -> subFolders.firstOrNull { it.id == id } }
}

class FolderDetailViewModel(
    private val repository: FoldersRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
    private val settings: Settings,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderDetailUiState())
    val state: StateFlow<FolderDetailUiState> = _state.asStateFlow()

    /**
     * Order subfolders by the same persisted sort the folder list screen uses,
     * so drilling into a folder keeps the chosen order instead of falling back
     * to raw server order.
     */
    private fun sortSubfolders(folders: List<FolderSummary>): List<FolderSummary> {
        val sort = readFolderSort(settings)
        return sortFolders(folders, sort, readFolderDirection(settings, sort))
    }

    fun open(folderId: String, name: String, parentFolderId: String?) {
        if (_state.value.folderId == folderId &&
            (_state.value.items.isNotEmpty() || _state.value.subFolders.isNotEmpty())
        ) {
            _state.update { it.copy(folderName = name, parentFolderId = parentFolderId) }
            return
        }
        _state.value = FolderDetailUiState(
            folderId = folderId,
            folderName = name,
            parentFolderId = parentFolderId,
            isLoading = true
        )
        viewModelScope.launch {
            runCatching {
                supervisorScope {
                    val assets = async { repository.assets(folderId) }
                    val details = async {
                        runCatching { repository.get(folderId) }.getOrNull()
                    }
                    assets.await() to details.await()
                }
            }
                .onSuccess { (items, details) ->
                    _state.update {
                        it.copy(
                            items = items,
                            subFolders = sortSubfolders(details?.subFolders.orEmpty()),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Failed to load folder")
                        )
                    }
                }
        }
    }

    /**
     * Re-fetch the open folder's assets and subfolders, bypassing [open]'s
     * already-loaded short-circuit. Used after creating a subfolder so the new
     * child shows up immediately.
     */
    fun refresh() {
        val folderId = _state.value.folderId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                supervisorScope {
                    val assets = async { repository.assets(folderId) }
                    val details = async {
                        runCatching { repository.get(folderId) }.getOrNull()
                    }
                    assets.await() to details.await()
                }
            }
                .onSuccess { (items, details) ->
                    _state.update {
                        it.copy(
                            items = items,
                            subFolders = sortSubfolders(details?.subFolders.orEmpty()),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Failed to load folder")
                        )
                    }
                }
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.map { item ->
                    if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
                }
            )
        }
    }

    fun applyAssetRemovedLocal(assetId: String) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.filterNot { it.id == assetId },
                selection = previous.selection - assetId
            )
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update { previous ->
            val next = previous.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            // Asset and subfolder selection are mutually exclusive — they share
            // the detail screen's selection top/bottom bars.
            previous.copy(selection = next, selectedSubfolderId = null)
        }
    }

    /** Long-press a subfolder to enter single-subfolder selection mode. */
    fun selectSubfolder(id: String) {
        _state.update { it.copy(selectedSubfolderId = id, selection = emptySet()) }
    }

    /** Tap an already-shown subfolder while selecting: switch target or clear. */
    fun toggleSubfolderSelection(id: String) {
        _state.update {
            it.copy(
                selectedSubfolderId = if (it.selectedSubfolderId == id) null else id,
                selection = emptySet()
            )
        }
    }

    fun clearSubfolderSelection() {
        _state.update { it.copy(selectedSubfolderId = null) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelectAll() {
        _state.update { previous ->
            val all = previous.items.mapTo(HashSet()) { it.id }
            previous.copy(selection = if (previous.selection == all) emptySet() else all)
        }
    }

    fun rename(name: String, onSuccess: (FolderSummary) -> Unit = {}) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        val currentParent = _state.value.parentFolderId
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.update(folderId, name.trim(), parentFolderId = currentParent) }
                .onSuccess { folder ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            folderName = folder.name,
                            parentFolderId = folder.parentFolderId
                        )
                    }
                    onSuccess(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to rename folder")
                        )
                    }
                }
        }
    }

    fun move(targetParentFolderId: String?, onSuccess: (FolderSummary) -> Unit = {}) {
        val folderId = _state.value.folderId ?: return
        val name = _state.value.folderName ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.update(folderId, name, parentFolderId = targetParentFolderId) }
                .onSuccess { folder ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            folderName = folder.name,
                            parentFolderId = folder.parentFolderId
                        )
                    }
                    onSuccess(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to move folder")
                        )
                    }
                }
        }
    }

    /**
     * Rename a direct subfolder of the open folder. Its parent stays the open
     * folder; on success the in-memory subfolder list is patched so the new name
     * shows without a full reload.
     */
    fun renameSubfolder(
        subfolderId: String,
        name: String,
        onSuccess: (FolderSummary) -> Unit = {}
    ) {
        val parentId = _state.value.folderId
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.update(subfolderId, name.trim(), parentFolderId = parentId) }
                .onSuccess { folder ->
                    _state.update { st ->
                        st.copy(
                            isMutating = false,
                            selectedSubfolderId = null,
                            subFolders = sortSubfolders(
                                st.subFolders.map { sf ->
                                    if (sf.id == folder.id) {
                                        sf.copy(name = folder.name, path = folder.path)
                                    } else sf
                                }
                            )
                        )
                    }
                    onSuccess(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to rename folder")
                        )
                    }
                }
        }
    }

    /**
     * Delete a direct subfolder of the open folder. The server rejects folders
     * that still hold assets; that error surfaces in the confirmation dialog.
     */
    fun deleteSubfolder(subfolderId: String, onSuccess: (String) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.delete(subfolderId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isMutating = false,
                            selectedSubfolderId = null,
                            subFolders = it.subFolders.filterNot { sf -> sf.id == subfolderId }
                        )
                    }
                    onSuccess(subfolderId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to delete folder")
                        )
                    }
                }
        }
    }

    fun moveSelectedAssets(targetFolderId: String, onSuccess: (movedIds: List<String>) -> Unit = {}) {
        val sourceFolderId = _state.value.folderId ?: return
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.moveAssets(
                    sourceFolderId = sourceFolderId,
                    targetFolderId = targetFolderId,
                    assetIds = ids
                )
            }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            items = previous.items.filterNot { it.id in ids },
                            selection = emptySet(),
                            isBulkMutating = false
                        )
                    }
                    onSuccess(ids)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "Failed to move assets")
                        )
                    }
                }
        }
    }

    fun bulkArchive() = runAssetBulk(
        action = { assetRepository.archive(it) },
        errorFallback = "Failed to archive"
    )

    fun bulkTrash() = runAssetBulk(
        action = { assetRepository.trash(it) },
        errorFallback = "Failed to delete"
    )

    fun bulkAddToAlbum(albumId: String, onSuccess: (List<TimelineItem>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val added = _state.value.items.filter { it.id in ids }
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
                            error = errorFactory.from(error, "Failed to add to album")
                        )
                    }
                }
        }
    }

    private fun runAssetBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previousItems = _state.value.items
        _state.update {
            it.copy(
                isBulkMutating = true,
                error = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            items = previousItems,
                            isBulkMutating = false,
                            error = errorFactory.from(error, errorFallback)
                        )
                    }
                }
        }
    }

    fun delete(onDeleted: (String) -> Unit) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.delete(folderId) }
                .onSuccess {
                    _state.value = FolderDetailUiState()
                    onDeleted(folderId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to delete folder")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
