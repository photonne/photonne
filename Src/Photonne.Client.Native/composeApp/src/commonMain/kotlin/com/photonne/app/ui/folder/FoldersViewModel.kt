package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.moveDestinationFolders
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.ui.util.SortDirection
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FolderSort { Name, AssetCount }

enum class FolderViewMode { List, Grid }

data class FoldersUiState(
    // Top-level entries per bucket: direct children of the user's home, of the
    // shared space, and one root per external library.
    val personalFolders: List<FolderSummary> = emptyList(),
    val sharedFolders: List<FolderSummary> = emptyList(),
    val externalRoots: List<FolderSummary> = emptyList(),
    // Same subtrees at full depth. Search only: the list browses roots, but the
    // search field looks all the way down.
    val personalDescendants: List<FolderSummary> = emptyList(),
    val sharedDescendants: List<FolderSummary> = emptyList(),
    val externalDescendants: List<FolderSummary> = emptyList(),
    // Full-depth writable destinations (personal + shared subtrees) for the
    // move picker, which renders them as an indented tree.
    val moveDestinations: List<FolderSummary> = emptyList(),
    // Live count of unorganized (MobileBackup) assets, shown on the "Para
    // organizar" entry card.
    val organizePendingCount: Int = 0,
    val scope: FoldersScope = FoldersScope.All,
    val sort: FolderSort = FolderSort.Name,
    val direction: SortDirection = SortDirection.Ascending,
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

    /** Scope hides folders, so the Tune icon has to advertise it. */
    val isFilterActive: Boolean get() = scope != FoldersScope.All

    /**
     * The single list the screen renders.
     *
     * `distinctBy` is a guard, not a tidy-up: the buckets are disjoint only as
     * long as the server keeps `isShared` and `externalLibraryId` consistent
     * with the path, and a duplicate id would make `LazyColumn` throw rather
     * than merely repeat a row.
     */
    val visibleFolders: List<FolderSummary>
        get() {
            val source = if (hasActiveQuery) searchSource() else scopedRoots()
            return sortFolders(
                source.distinctBy { it.id }.filterByQuery(searchQuery),
                sort,
                direction
            )
        }

    /**
     * The folder behind [selectedFolderId], looked up across every bucket at
     * full depth — a long-press can land on an external root or, while
     * searching, on a folder nested well below any root.
     */
    fun findFolder(id: String?): FolderSummary? {
        if (id == null) return null
        return personalDescendants.firstOrNull { it.id == id }
            ?: sharedDescendants.firstOrNull { it.id == id }
            ?: externalDescendants.firstOrNull { it.id == id }
    }

    private fun scopedRoots(): List<FolderSummary> = when (scope) {
        FoldersScope.All -> personalFolders + sharedFolders + externalRoots
        FoldersScope.Personal -> personalFolders
        FoldersScope.Shared -> sharedFolders
        FoldersScope.External -> externalRoots
    }

    private fun searchSource(): List<FolderSummary> = when (scope) {
        FoldersScope.All -> personalDescendants + sharedDescendants + externalDescendants
        FoldersScope.Personal -> personalDescendants
        FoldersScope.Shared -> sharedDescendants
        FoldersScope.External -> externalDescendants
    }
}

private fun List<FolderSummary>.filterByQuery(query: String): List<FolderSummary> {
    if (query.isBlank()) return this
    val needle = query.trim().lowercase()
    return filter { folder -> folder.name.lowercase().contains(needle) }
}

class FoldersViewModel(
    private val repository: FoldersRepository,
    private val organizeRepository: OrganizeRepository,
    private val authState: AuthStateHolder,
    private val settings: Settings,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    private var allFolders: List<FolderSummary> = emptyList()
    private var refreshJob: Job? = null

    init { refresh() }

    /**
     * Reloads the "Para organizar" pending count (cheap standalone query).
     * Called on refresh and after any move that could file assets out of
     * MobileBackup, so the entry-card badge stays honest.
     */
    fun refreshOrganizeCount() {
        viewModelScope.launch {
            runCatching { organizeRepository.count() }
                .onSuccess { count -> _state.update { it.copy(organizePendingCount = count) } }
        }
    }

    private fun loadInitialState(): FoldersUiState {
        val sort = readFolderSort(settings)
        val direction = readFolderDirection(settings, sort)
        val viewMode = settings.getStringOrNull(KEY_VIEW_MODE)
            ?.let { runCatching { FolderViewMode.valueOf(it) }.getOrNull() }
            ?: FolderViewMode.List
        return FoldersUiState(sort = sort, direction = direction, viewMode = viewMode)
    }

    /**
     * Deliberately not persisted, unlike sort/direction/view mode: those are
     * presentational, this one hides folders. A subtractive filter restored
     * weeks later — with only a tinted Tune icon to explain it — reads as
     * missing data.
     */
    fun setScope(scope: FoldersScope) {
        _state.update { it.copy(scope = scope, selectedFolderId = null) }
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
        // Picking a criterion resets to its natural direction; the user can then
        // flip it explicitly with setDirection.
        val direction = sort.defaultDirection()
        settings.putString(FOLDERS_SORT_KEY, sort.name)
        settings.putString(FOLDERS_DIRECTION_KEY, direction.name)
        _state.update { it.copy(sort = sort, direction = direction) }
        repartition()
    }

    fun setDirection(direction: SortDirection) {
        settings.putString(FOLDERS_DIRECTION_KEY, direction.name)
        _state.update { it.copy(direction = direction) }
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
        refreshOrganizeCount()
        refreshJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        refreshJob = viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { folders ->
                    allFolders = folders
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedFolderId = it.selectedFolderId?.takeIf { id ->
                                folders.any { f -> f.id == id }
                            }
                        )
                    }
                    repartition()
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

    /**
     * Per-user opt-out: include/exclude a shared folder from my timeline,
     * memories, people and search. The folder stays browsable/administrable —
     * only my discovery surfaces change.
     */
    fun setTimelineIncluded(folderId: String, included: Boolean, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setTimelineIncluded(folderId, included) }
                .onSuccess {
                    allFolders = allFolders.map {
                        if (it.id == folderId) it.copy(excludedFromDiscovery = !included) else it
                    }
                    repartition()
                    _state.update { it.copy(isMutating = false, selectedFolderId = null) }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to update timeline visibility")
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

    /**
     * Single place the folder list is split into buckets, so a mutation applied
     * to [allFolders] can't leave one of them stale.
     *
     * The stored lists stay sorted because the folder pickers read them
     * directly; [FoldersUiState.visibleFolders] sorts again because "Todas"
     * concatenates three buckets and a concatenation of sorted lists isn't
     * sorted.
     */
    private fun repartition() {
        val username = (authState.state.value as? AuthState.Authenticated)?.user?.username
        val partition = partitionFolders(allFolders, username)
        val sort = _state.value.sort
        val direction = _state.value.direction
        fun sorted(folders: List<FolderSummary>) = sortFolders(folders, sort, direction)
        _state.update {
            it.copy(
                personalFolders = sorted(partition.personalRoots),
                sharedFolders = sorted(partition.sharedRoots),
                externalRoots = sorted(partition.externalRoots),
                personalDescendants = partition.personalDescendants,
                sharedDescendants = partition.sharedDescendants,
                externalDescendants = partition.externalDescendants,
                moveDestinations = sorted(moveDestinationFolders(allFolders))
            )
        }
    }

    private companion object {
        private const val KEY_VIEW_MODE = "photonne.folders.viewMode"
    }
}

