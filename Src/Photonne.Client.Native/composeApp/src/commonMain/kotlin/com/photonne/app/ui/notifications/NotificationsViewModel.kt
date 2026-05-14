package com.photonne.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.NotificationDto
import com.photonne.app.data.notifications.NotificationsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = PAGE_SIZE,
    val totalPages: Int = 0,
    val unreadOnly: Boolean = false,
    val isLoading: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val errorMessage: String? = null,
    val loaded: Boolean = false
) {
    val hasMorePages: Boolean get() = page < totalPages
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isLoading

    companion object {
        const val PAGE_SIZE = 20
    }
}

/**
 * Backs the Notifications page and the bell-icon badge. Mirrors the PWA's
 * `Notifications.razor` (page) and `MainLayout.razor` polling loop: an
 * unread counter that the top app bar reads from, and a paged list with
 * a filter chip (All / Unread only).
 */
class NotificationsViewModel(
    private val repository: NotificationsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        startUnreadPolling()
    }

    /** Loads (or reloads) page 1 with the current `unreadOnly` filter. */
    fun ensureLoaded() {
        if (_state.value.loaded || _state.value.isLoading) return
        load(page = 1)
    }

    fun refresh() {
        load(page = _state.value.page.coerceAtLeast(1))
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        if (_state.value.unreadOnly == unreadOnly) return
        _state.update { it.copy(unreadOnly = unreadOnly, page = 1, loaded = false) }
        load(page = 1)
    }

    fun goToPage(page: Int) {
        if (page < 1) return
        load(page = page)
    }

    fun markRead(id: String) {
        val target = _state.value.items.firstOrNull { it.id == id } ?: return
        if (target.isRead) return
        // Optimistic: flip the flag locally and decrement the unread badge so
        // the UI feels instant. We rely on the next poll/refresh to catch any
        // server-side divergence.
        _state.update { snapshot ->
            snapshot.copy(
                items = snapshot.items.map {
                    if (it.id == id) it.copy(isRead = true) else it
                },
                unreadCount = (snapshot.unreadCount - 1).coerceAtLeast(0)
            )
        }
        viewModelScope.launch {
            runCatching { repository.markRead(id) }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Failed to mark as read")
                    }
                }
        }
    }

    fun markAllRead() {
        val snapshot = _state.value
        if (snapshot.isMarkingAllRead || snapshot.unreadCount == 0) return
        _state.update { it.copy(isMarkingAllRead = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.markAllRead() }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            isMarkingAllRead = false,
                            items = current.items.map { it.copy(isRead = true) },
                            unreadCount = 0
                        )
                    }
                    // If the user was filtering by unread, the visible list
                    // should now collapse — refetch to keep totals honest.
                    if (_state.value.unreadOnly) load(page = 1)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMarkingAllRead = false,
                            errorMessage = error.message ?: "Failed to mark all as read"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun load(page: Int) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.list(
                    page = page,
                    pageSize = NotificationsUiState.PAGE_SIZE,
                    unreadOnly = _state.value.unreadOnly
                )
            }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            items = result.items,
                            totalCount = result.totalCount,
                            page = result.page.coerceAtLeast(1),
                            pageSize = result.pageSize,
                            totalPages = result.totalPages,
                            unreadCount = result.unreadCount,
                            isLoading = false,
                            loaded = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load notifications"
                        )
                    }
                }
        }
    }

    private fun startUnreadPolling() {
        viewModelScope.launch {
            while (true) {
                runCatching { repository.unreadCount() }
                    .onSuccess { count ->
                        _state.update { it.copy(unreadCount = count) }
                    }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
    }
}
