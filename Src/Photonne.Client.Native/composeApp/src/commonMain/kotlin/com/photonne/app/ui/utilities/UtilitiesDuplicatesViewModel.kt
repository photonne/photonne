package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.UserDuplicateGroup
import com.photonne.app.data.utilities.UtilitiesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * View state for a single duplicate group. We keep the asset list flat
 * but track per-asset selection here so the UI can offer "keep newest,
 * delete the rest" semantics — the most common cleanup intent.
 *
 * `wastedBytes` is the total minus the largest asset (the one we'd
 * keep if the user accepts auto-selection), exposing how much space
 * the user could reclaim by acting on this group.
 */
data class DuplicateGroupView(
    val group: UserDuplicateGroup,
    val selectedAssetIds: Set<String>
) {
    val wastedBytes: Long
        get() = group.assets
            .filter { it.id in selectedAssetIds }
            .sumOf { it.fileSize }
}

data class UtilitiesDuplicatesUiState(
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val groups: List<DuplicateGroupView> = emptyList()
) {
    val totalSelectedCount: Int get() = groups.sumOf { it.selectedAssetIds.size }
    val totalSelectedBytes: Long get() = groups.sumOf { it.wastedBytes }
    val totalWastedBytes: Long
        get() = groups.sumOf { view ->
            val keep = view.group.assets.maxByOrNull { it.fileSize }?.fileSize ?: 0L
            view.group.totalSize - keep
        }
}

class UtilitiesDuplicatesViewModel(
    private val repository: UtilitiesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UtilitiesDuplicatesUiState())
    val state: StateFlow<UtilitiesDuplicatesUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.groups.isNotEmpty() || _state.value.isLoading) return
        load()
    }

    fun refresh() = load()

    private fun load() {
        if (_state.value.isLoading) return
        _state.update {
            it.copy(isLoading = true, errorMessage = null, statusMessage = null)
        }
        viewModelScope.launch {
            runCatching { repository.duplicates() }
                .onSuccess { groups ->
                    _state.update {
                        it.copy(
                            groups = groups.map { g -> DuplicateGroupView(g, emptySet()) },
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load duplicates"
                        )
                    }
                }
        }
    }

    fun toggleAsset(groupHash: String, assetId: String) {
        _state.update { current ->
            current.copy(
                groups = current.groups.map { view ->
                    if (view.group.hash != groupHash) view
                    else view.copy(
                        selectedAssetIds = view.selectedAssetIds.toMutableSet().also { set ->
                            if (assetId in set) set.remove(assetId) else set.add(assetId)
                        }
                    )
                }
            )
        }
    }

    /**
     * Pre-selects every duplicate except the largest in each group, so
     * the user can review and click "Delete" without having to tap
     * through every asset individually.
     */
    fun autoSelectKeepingBest() {
        _state.update { current ->
            current.copy(
                groups = current.groups.map { view ->
                    val keep = view.group.assets.maxByOrNull { it.fileSize }?.id
                    val selected = view.group.assets
                        .map { it.id }
                        .filter { it != keep }
                        .toSet()
                    view.copy(selectedAssetIds = selected)
                }
            )
        }
    }

    fun clearSelection() {
        _state.update { current ->
            current.copy(groups = current.groups.map { it.copy(selectedAssetIds = emptySet()) })
        }
    }

    fun deleteSelected() {
        val selected = _state.value.groups.flatMap { it.selectedAssetIds }
        if (selected.isEmpty() || _state.value.isDeleting) return
        _state.update {
            it.copy(isDeleting = true, errorMessage = null, statusMessage = null)
        }
        viewModelScope.launch {
            runCatching { repository.deleteAssets(selected) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            statusMessage = "${selected.size} elementos enviados a la papelera"
                        )
                    }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = error.message ?: "Could not delete"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, statusMessage = null) }
    }
}
