package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.ui.util.SortDirection
import com.photonne.app.ui.util.applyDirection
import com.photonne.app.ui.util.parseSortDirection
import com.photonne.app.ui.util.sortedByNatural
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which slice of the album list the user is looking at. */
enum class AlbumsScope { All, Mine, Shared }

enum class AlbumSort { Date, Name }

/** Natural default direction when a criterion is freshly picked. */
fun AlbumSort.defaultDirection(): SortDirection = when (this) {
    AlbumSort.Date -> SortDirection.Descending // newest first
    AlbumSort.Name -> SortDirection.Ascending // A→Z
}

enum class AlbumViewMode { Grid, List }

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
    val scope: AlbumsScope = AlbumsScope.All,
    val sort: AlbumSort = AlbumSort.Date,
    val direction: SortDirection = SortDirection.Descending,
    val viewMode: AlbumViewMode = AlbumViewMode.Grid,
    val groupByYear: Boolean = false,
    val selectedAlbumId: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    val isSelectionActive: Boolean get() = selectedAlbumId != null

    val hasActiveQuery: Boolean get() = searchQuery.isNotBlank()

    /** Scope hides albums, so the Tune icon has to advertise it. */
    val isFilterActive: Boolean get() = scope != AlbumsScope.All

    val visibleAlbums: List<AlbumSummary> get() {
        // Mine and Shared partition the list exactly: every album is either
        // mine-and-private or shared in one direction or the other.
        val scopeFiltered = when (scope) {
            AlbumsScope.All -> albums
            AlbumsScope.Mine -> albums.filter { it.isOwner && !it.isShared }
            AlbumsScope.Shared -> albums.filter { !it.isOwner || it.isShared }
        }
        val queryFiltered = if (hasActiveQuery) {
            val needle = searchQuery.trim().lowercase()
            scopeFiltered.filter { album ->
                album.name.lowercase().contains(needle) ||
                    (album.description?.lowercase()?.contains(needle) == true)
            }
        } else scopeFiltered
        val ascending = when (sort) {
            AlbumSort.Date -> queryFiltered.sortedBy { it.createdAt }
            AlbumSort.Name -> queryFiltered.sortedByNatural { it.name }
        }
        return ascending.applyDirection(direction)
    }
}

class AlbumsViewModel(
    private val repository: AlbumsRepository,
    private val settings: Settings,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init { refresh() }

    private fun loadInitialState(): AlbumsUiState {
        // Legacy values "Recent"/"Oldest" collapse into (Date, direction); newer
        // installs persist the criterion and direction separately.
        val rawSort = settings.getStringOrNull(KEY_SORT)
        val (sort, migratedDirection) = when (rawSort) {
            "Recent" -> AlbumSort.Date to SortDirection.Descending
            "Oldest" -> AlbumSort.Date to SortDirection.Ascending
            else -> (runCatching { AlbumSort.valueOf(rawSort ?: "") }.getOrNull()
                ?: AlbumSort.Date) to null
        }
        val direction = parseSortDirection(
            settings.getStringOrNull(KEY_DIRECTION),
            migratedDirection ?: sort.defaultDirection()
        )
        val viewMode = settings.getStringOrNull(KEY_VIEW_MODE)
            ?.let { runCatching { AlbumViewMode.valueOf(it) }.getOrNull() }
            ?: AlbumViewMode.Grid
        val groupByYear = settings.getBoolean(KEY_GROUP_BY_YEAR, false)
        return AlbumsUiState(
            sort = sort,
            direction = direction,
            viewMode = viewMode,
            groupByYear = groupByYear
        )
    }

    /**
     * Deliberately not persisted, unlike sort/direction/view mode: those are
     * presentational, this one hides albums. A subtractive filter restored
     * weeks later — with only a tinted Tune icon to explain it — reads as
     * missing data.
     */
    fun setScope(scope: AlbumsScope) {
        _state.update { it.copy(scope = scope, selectedAlbumId = null) }
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

    fun setSort(sort: AlbumSort) {
        // Picking a criterion resets to its natural direction; the user can then
        // flip it explicitly with setDirection.
        val direction = sort.defaultDirection()
        settings.putString(KEY_SORT, sort.name)
        settings.putString(KEY_DIRECTION, direction.name)
        _state.update { it.copy(sort = sort, direction = direction) }
    }

    fun setDirection(direction: SortDirection) {
        settings.putString(KEY_DIRECTION, direction.name)
        _state.update { it.copy(direction = direction) }
    }

    fun setViewMode(mode: AlbumViewMode) {
        settings.putString(KEY_VIEW_MODE, mode.name)
        _state.update { it.copy(viewMode = mode) }
    }

    fun setGroupByYear(enabled: Boolean) {
        settings.putBoolean(KEY_GROUP_BY_YEAR, enabled)
        _state.update { it.copy(groupByYear = enabled) }
    }

    fun selectAlbum(id: String) {
        _state.update { it.copy(selectedAlbumId = id) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedAlbumId = null) }
    }

    fun refresh() {
        refreshJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        refreshJob = viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { albums ->
                    _state.update {
                        it.copy(
                            albums = albums,
                            isLoading = false,
                            error = null,
                            selectedAlbumId = it.selectedAlbumId?.takeIf { id ->
                                albums.any { a -> a.id == id }
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
                            error = errorFactory.from(error, "Failed to load albums")
                        )
                    }
                }
        }
    }

    fun create(name: String, description: String?, onCreated: (AlbumSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.create(name.trim(), description?.trim()?.takeIf { it.isNotEmpty() }) }
                .onSuccess { album ->
                    _state.update {
                        it.copy(albums = listOf(album) + it.albums, isMutating = false)
                    }
                    onCreated(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to create album")
                        )
                    }
                }
        }
    }

    fun renameAlbum(
        albumId: String,
        name: String,
        description: String?,
        onSuccess: (AlbumSummary) -> Unit = {}
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.update(
                    albumId = albumId,
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
                .onSuccess { updated ->
                    _state.update { previous ->
                        previous.copy(
                            albums = previous.albums.map { if (it.id == updated.id) updated else it },
                            isMutating = false,
                            selectedAlbumId = null
                        )
                    }
                    onSuccess(updated)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to rename album")
                        )
                    }
                }
        }
    }

    fun deleteAlbum(albumId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.delete(albumId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            albums = previous.albums.filterNot { it.id == albumId },
                            isMutating = false,
                            selectedAlbumId = null
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to delete album")
                        )
                    }
                }
        }
    }

    fun leaveAlbum(albumId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.leave(albumId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            albums = previous.albums.filterNot { it.id == albumId },
                            isMutating = false,
                            selectedAlbumId = null
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to leave album")
                        )
                    }
                }
        }
    }

    fun applyUpdate(updated: AlbumSummary) {
        _state.update { previous ->
            previous.copy(albums = previous.albums.map { if (it.id == updated.id) updated else it })
        }
    }

    fun applyDelete(albumId: String) {
        _state.update { previous ->
            previous.copy(albums = previous.albums.filterNot { it.id == albumId })
        }
    }

    fun applyAssetAdded(albumId: String) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(assetCount = it.assetCount + 1) else it
                }
            )
        }
    }

    fun applyAssetsAdded(albumId: String, addedCount: Int) {
        if (addedCount <= 0) return
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(assetCount = it.assetCount + addedCount) else it
                }
            )
        }
    }

    fun applyAssetRemoved(albumId: String) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) {
                        it.copy(assetCount = (it.assetCount - 1).coerceAtLeast(0))
                    } else it
                }
            )
        }
    }

    fun applyAssetsRemoved(albumId: String, removedCount: Int) {
        if (removedCount <= 0) return
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) {
                        it.copy(assetCount = (it.assetCount - removedCount).coerceAtLeast(0))
                    } else it
                }
            )
        }
    }

    fun applyShareLinkChanged(albumId: String, hasActiveShareLink: Boolean) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(hasActiveShareLink = hasActiveShareLink) else it
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private companion object {
        private const val KEY_SORT = "photonne.albums.sort"
        private const val KEY_DIRECTION = "photonne.albums.sortDirection"
        private const val KEY_VIEW_MODE = "photonne.albums.viewMode"
        private const val KEY_GROUP_BY_YEAR = "photonne.albums.groupByYear"
    }
}
