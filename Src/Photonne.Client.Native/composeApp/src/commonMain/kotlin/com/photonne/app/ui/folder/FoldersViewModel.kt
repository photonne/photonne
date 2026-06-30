package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.folder.filterSharedFolders
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.FolderSummary
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

enum class FoldersTab { Personal, Shared, Libraries }

enum class FolderSort { Name, AssetCount }

enum class FolderViewMode { List, Grid }

data class FoldersUiState(
    val personalFolders: List<FolderSummary> = emptyList(),
    val sharedFolders: List<FolderSummary> = emptyList(),
    val libraries: List<ExternalLibraryDto> = emptyList(),
    val libraryFolders: List<FolderSummary> = emptyList(),
    val libraryRoots: Map<String, FolderSummary> = emptyMap(),
    val selectedTab: FoldersTab = FoldersTab.Personal,
    val sort: FolderSort = FolderSort.Name,
    val viewMode: FolderViewMode = FolderViewMode.List,
    val selectedFolderId: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    val isSelectionActive: Boolean get() = selectedFolderId != null

    val hasActiveQuery: Boolean get() = searchQuery.isNotBlank()

    val visiblePersonalFolders: List<FolderSummary>
        get() = personalFolders.filterByQuery(searchQuery)

    val visibleSharedFolders: List<FolderSummary>
        get() = sharedFolders.filterByQuery(searchQuery)

    val visibleLibraries: List<ExternalLibraryDto>
        get() {
            if (!hasActiveQuery) return libraries
            val needle = searchQuery.trim().lowercase()
            return libraries.filter { it.name.lowercase().contains(needle) }
        }

    val visibleLibraryFolders: List<FolderSummary>
        get() = libraryFolders.filterByQuery(searchQuery)
}

private fun List<FolderSummary>.filterByQuery(query: String): List<FolderSummary> {
    if (query.isBlank()) return this
    val needle = query.trim().lowercase()
    return flattenFolders()
        .distinctBy { it.id }
        .filter { folder -> folder.name.lowercase().contains(needle) }
}

private fun List<FolderSummary>.flattenFolders(): List<FolderSummary> {
    val out = mutableListOf<FolderSummary>()
    fun visit(folder: FolderSummary) {
        out.add(folder)
        folder.subFolders.forEach(::visit)
    }
    forEach(::visit)
    return out
}

class FoldersViewModel(
    private val repository: FoldersRepository,
    private val authState: AuthStateHolder,
    private val settings: Settings,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    private var allFolders: List<FolderSummary> = emptyList()
    private var refreshJob: Job? = null

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

    fun toggleSearch() {
        _state.update {
            if (it.isSearchActive) it.copy(isSearchActive = false, searchQuery = "")
            else it.copy(isSearchActive = true)
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
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
        val username = (authState.state.value as? AuthState.Authenticated)?.user?.username
        refreshJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        refreshJob = viewModelScope.launch {
            runCatching {
                // supervisorScope keeps a child failure (e.g. /folders → 500)
                // from cancelling the parent launch and escaping runCatching
                // via the structured-concurrency channel — without it the
                // PhotonneApiException reaches the uncaught handler and the
                // process dies.
                supervisorScope {
                    val folders = async { repository.list() }
                    val libs = async {
                        runCatching { repository.listExternalLibraries() }.getOrDefault(emptyList())
                    }
                    folders.await() to libs.await()
                }
            }
                .onSuccess { (folders, libs) ->
                    allFolders = folders
                    val personalChildren = if (username.isNullOrBlank()) emptyList()
                        else filterPersonalFolders(folders, username)
                    val sort = _state.value.sort
                    val personal = sortFolders(personalChildren.filter { !it.isShared }, sort)
                    val shared = sortFolders(filterSharedFolders(folders), sort)
                    val libRoots = resolveLibraryRoots(folders)
                    val libFolders = sortFolders(folders.filter { it.externalLibraryId != null }, sort)
                    _state.update {
                        it.copy(
                            personalFolders = personal,
                            sharedFolders = shared,
                            libraries = sortLibraries(libs, sort),
                            libraryFolders = libFolders,
                            libraryRoots = libRoots,
                            isLoading = false,
                            selectedFolderId = it.selectedFolderId?.takeIf { id ->
                                folders.any { f -> f.id == id }
                            }
                        )
                    }
                }
                .onFailure { error ->
                    // A refresh() that supersedes this one (e.g. the effective
                    // URL flipping LAN↔público) cancels this job; that is not a
                    // load error, so honour cancellation and rethrow rather than
                    // paint a banner over the successful reload.
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Failed to load folders")
                        )
                    }
                }
        }
    }

    fun resolveLibraryRoot(libraryId: String): FolderSummary? =
        _state.value.libraryRoots[libraryId]

    fun create(
        name: String,
        parentFolderId: String?,
        isSharedSpace: Boolean = false,
        onCreated: (FolderSummary) -> Unit = {}
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.create(
                    name = name.trim(),
                    parentFolderId = parentFolderId,
                    isSharedSpace = isSharedSpace
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
                            error = errorFactory.from(error, "Failed to create folder")
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
        _state.update { it.copy(isMutating = true, error = null) }
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
                            error = errorFactory.from(error, "Failed to rename folder")
                        )
                    }
                }
        }
    }

    fun deleteFolder(folderId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
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
                            error = errorFactory.from(error, "Failed to delete folder")
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
        _state.update { it.copy(error = null) }
    }

    private fun repartition() {
        val username = (authState.state.value as? AuthState.Authenticated)?.user?.username
        val personalChildren = if (username.isNullOrBlank()) emptyList()
            else filterPersonalFolders(allFolders, username)
        val sort = _state.value.sort
        val personal = sortFolders(personalChildren.filter { !it.isShared }, sort)
        val shared = sortFolders(filterSharedFolders(allFolders), sort)
        val libFolders = sortFolders(allFolders.filter { it.externalLibraryId != null }, sort)
        _state.update {
            it.copy(
                personalFolders = personal,
                sharedFolders = shared,
                libraryFolders = libFolders,
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

private fun resolveLibraryRoots(folders: List<FolderSummary>): Map<String, FolderSummary> {
    val byId = folders.associateBy { it.id }
    return folders
        .filter { folder ->
            val libId = folder.externalLibraryId ?: return@filter false
            val parentId = folder.parentFolderId ?: return@filter true
            byId[parentId]?.externalLibraryId != libId
        }
        .associateBy { it.externalLibraryId!! }
}

