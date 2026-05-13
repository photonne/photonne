package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.FolderSummary
import com.russhwolf.settings.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoldersTab { Personal, Shared, Libraries }

enum class FolderSort { Name, AssetCount }

enum class FolderViewMode { List, Grid }

data class FoldersUiState(
    val personalFolders: List<FolderSummary> = emptyList(),
    val sharedFolders: List<FolderSummary> = emptyList(),
    val libraries: List<ExternalLibraryDto> = emptyList(),
    val libraryRoots: Map<String, FolderSummary> = emptyMap(),
    val selectedTab: FoldersTab = FoldersTab.Personal,
    val sort: FolderSort = FolderSort.Name,
    val viewMode: FolderViewMode = FolderViewMode.List,
    val selectedFolderId: String? = null,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionActive: Boolean get() = selectedFolderId != null
}

class FoldersViewModel(
    private val repository: FoldersRepository,
    private val authState: AuthStateHolder,
    private val settings: Settings
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    private var allFolders: List<FolderSummary> = emptyList()

    init { refresh() }

    private fun loadInitialState(): FoldersUiState {
        val sort = settings.getStringOrNull(KEY_SORT)
            ?.let { runCatching { FolderSort.valueOf(it) }.getOrNull() }
            ?: FolderSort.Name
        val viewMode = settings.getStringOrNull(KEY_VIEW_MODE)
            ?.let { runCatching { FolderViewMode.valueOf(it) }.getOrNull() }
            ?: FolderViewMode.List
        return FoldersUiState(sort = sort, viewMode = viewMode)
    }

    fun selectTab(tab: FoldersTab) {
        _state.update { it.copy(selectedTab = tab, selectedFolderId = null) }
    }

    fun setSort(sort: FolderSort) {
        settings.putString(KEY_SORT, sort.name)
        _state.update { it.copy(sort = sort) }
        repartition()
    }

    fun setViewMode(mode: FolderViewMode) {
        settings.putString(KEY_VIEW_MODE, mode.name)
        _state.update { it.copy(viewMode = mode) }
    }

    fun selectFolder(id: String) {
        _state.update { it.copy(selectedFolderId = id) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFolderId = null) }
    }

    fun refresh() {
        val userId = (authState.state.value as? AuthState.Authenticated)?.user?.id
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val folders = async { repository.list() }
                val libs = async {
                    runCatching { repository.listExternalLibraries() }.getOrDefault(emptyList())
                }
                awaitAll(folders, libs)
                folders.await() to libs.await()
            }
                .onSuccess { (folders, libs) ->
                    allFolders = folders
                    val baseScope = if (userId.isNullOrBlank()) folders
                        else filterPersonalFolders(folders, userId)
                    val sort = _state.value.sort
                    val personal = sortFolders(baseScope.filter { !it.isShared }, sort)
                    val shared = sortFolders(folders.filter { isSharedFolder(it) }, sort)
                    val libRoots = folders
                        .filter { it.externalLibraryId != null && it.parentFolderId == null }
                        .associateBy { it.externalLibraryId!! }
                    _state.update {
                        it.copy(
                            personalFolders = personal,
                            sharedFolders = shared,
                            libraries = sortLibraries(libs, sort),
                            libraryRoots = libRoots,
                            isLoading = false,
                            selectedFolderId = it.selectedFolderId?.takeIf { id ->
                                folders.any { f -> f.id == id }
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load folders"
                        )
                    }
                }
        }
    }

    fun resolveLibraryRoot(libraryId: String): FolderSummary? =
        _state.value.libraryRoots[libraryId]

    fun create(name: String, parentFolderId: String?, onCreated: (FolderSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.create(
                    name = name.trim(),
                    parentFolderId = parentFolderId,
                    isSharedSpace = false
                )
            }
                .onSuccess { folder ->
                    allFolders = listOf(folder) + allFolders
                    repartition()
                    _state.update { it.copy(isMutating = false) }
                    onCreated(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to create folder"
                        )
                    }
                }
        }
    }

    fun renameFolder(
        folderId: String,
        name: String,
        onSuccess: (FolderSummary) -> Unit = {}
    ) {
        val current = allFolders.firstOrNull { it.id == folderId } ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.update(
                    folderId = folderId,
                    name = name.trim(),
                    parentFolderId = current.parentFolderId
                )
            }
                .onSuccess { updated ->
                    allFolders = allFolders.map { if (it.id == updated.id) updated else it }
                    repartition()
                    _state.update { it.copy(isMutating = false, selectedFolderId = null) }
                    onSuccess(updated)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to rename folder"
                        )
                    }
                }
        }
    }

    fun deleteFolder(folderId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.delete(folderId) }
                .onSuccess {
                    allFolders = allFolders.filterNot { it.id == folderId }
                    repartition()
                    _state.update { it.copy(isMutating = false, selectedFolderId = null) }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to delete folder"
                        )
                    }
                }
        }
    }

    fun applyUpdate(updated: FolderSummary) {
        allFolders = allFolders.map { if (it.id == updated.id) updated else it }
        repartition()
    }

    fun applyDelete(folderId: String) {
        allFolders = allFolders.filterNot { it.id == folderId }
        repartition()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun repartition() {
        val userId = (authState.state.value as? AuthState.Authenticated)?.user?.id
        val baseScope = if (userId.isNullOrBlank()) allFolders
            else filterPersonalFolders(allFolders, userId)
        val sort = _state.value.sort
        val personal = sortFolders(baseScope.filter { !it.isShared }, sort)
        val shared = sortFolders(allFolders.filter { isSharedFolder(it) }, sort)
        _state.update {
            it.copy(
                personalFolders = personal,
                sharedFolders = shared,
                libraries = sortLibraries(it.libraries, sort)
            )
        }
    }

    private companion object {
        private const val KEY_SORT = "photonne.folders.sort"
        private const val KEY_VIEW_MODE = "photonne.folders.viewMode"
    }
}

private fun sortFolders(folders: List<FolderSummary>, sort: FolderSort): List<FolderSummary> =
    when (sort) {
        FolderSort.Name -> folders.sortedBy { it.name.ifBlank { it.path }.lowercase() }
        FolderSort.AssetCount -> folders.sortedByDescending { it.assetCount }
    }

private fun sortLibraries(
    libs: List<ExternalLibraryDto>,
    sort: FolderSort
): List<ExternalLibraryDto> = when (sort) {
    FolderSort.Name -> libs.sortedBy { it.name.lowercase() }
    FolderSort.AssetCount -> libs.sortedByDescending { it.assetCount }
}

private fun isSharedFolder(folder: FolderSummary): Boolean {
    if (folder.isShared) return true
    val normalized = folder.path.replace('\\', '/')
    return normalized.startsWith("/assets/shared", ignoreCase = true)
}
