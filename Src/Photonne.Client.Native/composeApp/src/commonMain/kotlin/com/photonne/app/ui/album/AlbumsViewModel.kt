package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.models.AlbumSummary
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AlbumsTab { Mine, Shared, MyLinks }

enum class AlbumSort { Recent, Oldest, Name }

enum class AlbumViewMode { Grid, List }

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
    val selectedTab: AlbumsTab = AlbumsTab.Mine,
    val sort: AlbumSort = AlbumSort.Recent,
    val viewMode: AlbumViewMode = AlbumViewMode.Grid,
    val groupByYear: Boolean = false,
    val selectedAlbumId: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionActive: Boolean get() = selectedAlbumId != null

    val hasActiveQuery: Boolean get() = searchQuery.isNotBlank()

    val visibleAlbums: List<AlbumSummary> get() {
        val tabFiltered = when (selectedTab) {
            AlbumsTab.Mine -> albums.filter { it.isOwner && !it.isShared }
            AlbumsTab.Shared -> albums.filter { !it.isOwner || it.isShared }
            AlbumsTab.MyLinks -> albums.filter { it.isOwner && it.hasActiveShareLink }
        }
        val queryFiltered = if (hasActiveQuery) {
            val needle = searchQuery.trim().lowercase()
            tabFiltered.filter { album ->
                album.name.lowercase().contains(needle) ||
                    (album.description?.lowercase()?.contains(needle) == true)
            }
        } else tabFiltered
        return when (sort) {
            AlbumSort.Recent -> queryFiltered.sortedByDescending { it.createdAt }
            AlbumSort.Oldest -> queryFiltered.sortedBy { it.createdAt }
            AlbumSort.Name -> queryFiltered.sortedBy { it.name.lowercase() }
        }
    }
}

class AlbumsViewModel(
    private val repository: AlbumsRepository,
    private val settings: Settings
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init { refresh() }

    private fun loadInitialState(): AlbumsUiState {
        val sort = settings.getStringOrNull(KEY_SORT)
            ?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() }
            ?: AlbumSort.Recent
        val viewMode = settings.getStringOrNull(KEY_VIEW_MODE)
            ?.let { runCatching { AlbumViewMode.valueOf(it) }.getOrNull() }
            ?: AlbumViewMode.Grid
        val groupByYear = settings.getBoolean(KEY_GROUP_BY_YEAR, false)
        return AlbumsUiState(sort = sort, viewMode = viewMode, groupByYear = groupByYear)
    }

    fun selectTab(tab: AlbumsTab) {
        _state.update { it.copy(selectedTab = tab, selectedAlbumId = null) }
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
        settings.putString(KEY_SORT, sort.name)
        _state.update { it.copy(sort = sort) }
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
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { albums ->
                    _state.update {
                        it.copy(
                            albums = albums,
                            isLoading = false,
                            errorMessage = null,
                            selectedAlbumId = it.selectedAlbumId?.takeIf { id ->
                                albums.any { a -> a.id == id }
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load albums"
                        )
                    }
                }
        }
    }

    fun create(name: String, description: String?, onCreated: (AlbumSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to create album"
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
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to rename album"
                        )
                    }
                }
        }
    }

    fun deleteAlbum(albumId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to delete album"
                        )
                    }
                }
        }
    }

    fun leaveAlbum(albumId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to leave album"
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
        _state.update { it.copy(errorMessage = null) }
    }

    private companion object {
        private const val KEY_SORT = "photonne.albums.sort"
        private const val KEY_VIEW_MODE = "photonne.albums.viewMode"
        private const val KEY_GROUP_BY_YEAR = "photonne.albums.groupByYear"
    }
}
