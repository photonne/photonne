package com.photonne.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.library.UnsupportedFilesRepository
import com.photonne.app.data.models.UnsupportedFileItem
import com.photonne.app.ui.actions.AssetSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class UnsupportedFilesUiState(
    val items: List<UnsupportedFileItem> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDownloading: Boolean = false,
    val statusMessage: String? = null,
    val error: UiError? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true,
    val loaded: Boolean = false
) {
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isInitialLoading
}

class UnsupportedFilesViewModel(
    private val repository: UnsupportedFilesRepository,
    private val sharing: AssetSharing,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(UnsupportedFilesUiState())
    val state: StateFlow<UnsupportedFilesUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val snapshot = _state.value
        if (snapshot.loaded || snapshot.isInitialLoading) return
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                isRefreshing = it.loaded,
                isInitialLoading = !it.loaded,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { repository.listUnsupportedFiles(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isInitialLoading = false,
                            isRefreshing = false,
                            loaded = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = errorFactory.from(error, "Failed to load unsupported files")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isInitialLoading || !snapshot.hasMore) return
        val cursor = snapshot.nextCursor ?: return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch {
            runCatching { repository.listUnsupportedFiles(cursor = cursor) }
                .onSuccess { page ->
                    _state.update {
                        val existing = it.items.mapTo(HashSet()) { item -> item.id }
                        val appended = page.items.filter { item -> item.id !in existing }
                        it.copy(
                            items = it.items + appended,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "Failed to load more")
                        )
                    }
                }
        }
    }

    /**
     * Downloads the original bytes through the authenticated HttpClient (no
     * token in the URL) and hands them to the OS save/Downloads location via
     * the shared [AssetSharing] glue — same path as the asset download action.
     */
    fun download(file: UnsupportedFileItem) {
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                val content = repository.downloadOriginal(file.id)
                sharing.saveAsset(
                    bytes = content.bytes,
                    fileName = content.suggestedFileName.ifBlank { file.fileName },
                    mimeType = content.mimeType
                )
            }
                .onSuccess { saved ->
                    _state.update { it.copy(isDownloading = false, statusMessage = saved.displayName) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            error = errorFactory.from(error, "Download failed")
                        )
                    }
                }
        }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
