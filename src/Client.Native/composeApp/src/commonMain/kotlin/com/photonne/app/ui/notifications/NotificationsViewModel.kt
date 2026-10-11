package com.photonne.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.NotificationDto
import com.photonne.app.data.notifications.ActivityNotifications
import com.photonne.app.data.notifications.NotificationsRepository
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.Job
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
    val error: UiError? = null,
    val loaded: Boolean = false,
    /** Cargando la página siguiente al final de la lista (scroll infinito). */
    val isAppending: Boolean = false
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
    private val repository: NotificationsRepository,
    private val errorFactory: UiErrorFactory,
    private val activityNotifications: ActivityNotifications,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    /**
     * The page load and the infinite-scroll append in flight. A new load or a
     * filter change cancels both, and every response is dropped unless its
     * filter is still the active one: a slow "All" answer landing after
     * switching to "Unread" used to overwrite the unread list.
     */
    private var loadJob: Job? = null
    private var appendJob: Job? = null

    init {
        startUnreadPolling()
    }

    /**
     * Reloads page 1 with the current `unreadOnly` filter. Always refetches:
     * this ViewModel outlives the screen (it also feeds the bell badge), so a
     * load-once guard would show the previous visit's stale list until a manual
     * pull-to-refresh. Old items stay visible while the reload is in flight.
     */
    fun ensureLoaded() {
        if (_state.value.isLoading) return
        load(page = 1)
    }

    /**
     * Pull-to-refresh: back to page 1. Reloading the current page REPLACED the
     * list with just that page (after scrolling to page 3, refresh left only
     * page 3's items), and could ask for a page that no longer exists.
     */
    fun refresh() {
        load(page = 1)
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        if (_state.value.unreadOnly == unreadOnly) return
        _state.update { it.copy(unreadOnly = unreadOnly, page = 1, loaded = false, isAppending = false) }
        load(page = 1)
    }

    fun goToPage(page: Int) {
        if (page < 1) return
        load(page = page)
    }

    /**
     * Scroll infinito (punto 37): añade la página siguiente al final,
     * deduplicando por id, en lugar del par Anterior/Siguiente que
     * recargaba la lista entera y perdía la posición.
     */
    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isAppending || !snapshot.hasMorePages) return
        val filter = snapshot.unreadOnly
        _state.update { it.copy(isAppending = true) }
        appendJob = viewModelScope.launch {
            suspendRunCatching {
                repository.list(
                    page = snapshot.page + 1,
                    pageSize = NotificationsUiState.PAGE_SIZE,
                    unreadOnly = filter
                )
            }
                .onSuccess { result ->
                    _state.update { current ->
                        if (current.unreadOnly != filter) return@update current
                        val existing = current.items.mapTo(HashSet()) { it.id }
                        current.copy(
                            items = current.items +
                                result.items.filter { it.id !in existing },
                            totalCount = result.totalCount,
                            page = result.page.coerceAtLeast(1),
                            totalPages = result.totalPages,
                            unreadCount = result.unreadCount,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.unreadOnly != filter) it
                        else it.copy(
                            isAppending = false,
                            error = errorFactory.from(
                                error, "No se pudieron cargar más notificaciones"
                            )
                        )
                    }
                }
        }
    }

    fun markRead(id: String) {
        val target = _state.value.items.firstOrNull { it.id == id } ?: return
        if (target.isRead) return
        // Optimistic: flip the flag locally and decrement the unread badge so
        // the UI feels instant; undone below if the server says no (before, a
        // failure left it looking read until the next reload).
        _state.update { snapshot ->
            snapshot.copy(
                items = snapshot.items.map {
                    if (it.id == id) it.copy(isRead = true) else it
                },
                unreadCount = (snapshot.unreadCount - 1).coerceAtLeast(0)
            )
        }
        viewModelScope.launch {
            suspendRunCatching { repository.markRead(id) }
                .onFailure { error ->
                    // The badge goes back to the server's count when it can be
                    // read (the poll may have landed in between, so a blind +1
                    // could double count); otherwise just undo our -1.
                    val serverCount = suspendRunCatching { repository.unreadCount() }.getOrNull()
                    _state.update { current ->
                        val flipped = current.items.any { it.id == id && it.isRead }
                        current.copy(
                            items = current.items.map {
                                if (it.id == id) it.copy(isRead = false) else it
                            },
                            unreadCount = serverCount
                                ?: if (flipped) current.unreadCount + 1 else current.unreadCount,
                            error = errorFactory.from(error, "No se pudo marcar como leída")
                        )
                    }
                }
        }
    }

    fun markAllRead() {
        val snapshot = _state.value
        if (snapshot.isMarkingAllRead || snapshot.unreadCount == 0) return
        _state.update { it.copy(isMarkingAllRead = true, error = null) }
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
                            error = errorFactory.from(error, "No se pudieron marcar todas como leídas")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun load(page: Int) {
        loadJob?.cancel()
        appendJob?.cancel()
        val filter = _state.value.unreadOnly
        _state.update { it.copy(isLoading = true, isAppending = false, error = null) }
        loadJob = viewModelScope.launch {
            suspendRunCatching {
                repository.list(
                    page = page,
                    pageSize = NotificationsUiState.PAGE_SIZE,
                    unreadOnly = filter
                )
            }
                .onSuccess { result ->
                    // Past the end (the list shrank since): start over at page 1.
                    if (page > 1 && result.totalPages in 1 until page) {
                        load(page = 1)
                        return@onSuccess
                    }
                    _state.update {
                        if (it.unreadOnly != filter) return@update it
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
                        if (it.unreadOnly != filter) it
                        else it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudieron cargar las notificaciones")
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
                        // Lo que ya cuenta la campana no se vuelve a avisar
                        // fuera de la app.
                        activityNotifications.markSeen(count)
                    }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
    }
}
