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
import kotlin.time.Clock
import kotlin.time.Instant

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
    // Tarjetas seleccionadas en la lista. Una sola es el caso de siempre
    // (acciones de ese álbum); varias habilitan las acciones en bloque.
    val selectedAlbumIds: Set<String> = emptySet(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    val isSelectionActive: Boolean get() = selectedAlbumIds.isNotEmpty()

    /** Los álbumes seleccionados que siguen en la lista, en su orden. */
    val selectedAlbums: List<AlbumSummary>
        get() = albums.filter { it.id in selectedAlbumIds }

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

    /**
     * La sección "Fijados" solo sale sin búsqueda: al buscar, la lista es de
     * resultados y los fijados van en su sitio como los demás.
     */
    val showsPinnedSection: Boolean
        get() = !hasActiveQuery && albums.any { it.isPinned }

    /**
     * Fijados visibles (el ámbito se respeta), del último fijado al primero.
     * Vacía si [showsPinnedSection] es false.
     */
    val pinnedAlbums: List<AlbumSummary>
        get() = if (!showsPinnedSection) emptyList()
        else visibleAlbums.filter { it.isPinned }.sortedByDescending { it.pinnedAt }

    /**
     * Lo que va debajo de los fijados, en el orden elegido. Sin sección, es
     * [visibleAlbums] tal cual. Los fijados NO se repiten aquí: la misma
     * clave dos veces en una LazyColumn/LazyGrid la hace fallar.
     */
    val unpinnedAlbums: List<AlbumSummary>
        get() = if (!showsPinnedSection) visibleAlbums
        else visibleAlbums.filterNot { it.isPinned }
}

class AlbumsViewModel(
    private val repository: AlbumsRepository,
    private val settings: Settings,
    private val errorFactory: UiErrorFactory,
    mutationBus: com.photonne.app.data.events.AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        // Punto 52: archivar/borrar/restaurar cambia portadas y recuentos de
        // álbumes; el bus evita que App.kt tenga que acordarse de llamarnos.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is com.photonne.app.data.events.AssetMutation.Removed,
                    is com.photonne.app.data.events.AssetMutation.Restored,
                    is com.photonne.app.data.events.AssetMutation.Purged,
                    com.photonne.app.data.events.AssetMutation.AllChanged ->
                        refreshQuietly()
                    is com.photonne.app.data.events.AssetMutation.FavoriteChanged,
                    is com.photonne.app.data.events.AssetMutation.DateChanged -> Unit
                }
            }
        }
    }

    /**
     * Recarga oportunista tras una mutación de assets: sin spinner y con el
     * fallo en silencio (la lista visible sigue siendo válida; ya se
     * reintentará en el siguiente refresh explícito).
     */
    private fun refreshQuietly() {
        if (_state.value.albums.isEmpty() && !_state.value.isLoading) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { albums ->
                    _state.update {
                        it.copy(
                            albums = albums,
                            isLoading = false,
                            selectedAlbumIds = it.selectedAlbumIds.filterTo(mutableSetOf()) { id ->
                                albums.any { a -> a.id == id }
                            }
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
    }

    private var refreshJob: Job? = null

    // Álbumes con un fijar/desfijar en vuelo: un doble toque no lanza dos.
    private val pinsInFlight = mutableSetOf<String>()

    /**
     * Fija o desfija [albumId] para mí (optimista): la tarjeta cambia al
     * instante y, si el servidor falla, vuelve a como estaba y [onFailure]
     * recibe si se intentaba fijar (true) o desfijar (false).
     */
    fun togglePin(albumId: String, onFailure: (pinning: Boolean) -> Unit = {}) {
        val current = _state.value.albums.firstOrNull { it.id == albumId } ?: return
        if (!pinsInFlight.add(albumId)) return
        val pin = !current.isPinned
        val previousPinnedAt = current.pinnedAt
        setPinned(albumId, pin, if (pin) Clock.System.now() else null)
        viewModelScope.launch {
            try {
                repository.setPinned(albumId, pin)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setPinned(albumId, current.isPinned, previousPinnedAt)
                onFailure(pin)
            } finally {
                pinsInFlight.remove(albumId)
            }
        }
    }

    private fun setPinned(albumId: String, pinned: Boolean, pinnedAt: Instant?) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(isPinned = pinned, pinnedAt = pinnedAt) else it
                }
            )
        }
    }

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
        _state.update { it.copy(scope = scope, selectedAlbumIds = emptySet()) }
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

    /** Pulsación larga: entra en selección (o añade la tarjeta a la que hay). */
    fun selectAlbum(id: String) {
        _state.update { it.copy(selectedAlbumIds = it.selectedAlbumIds + id) }
    }

    /** Toque con la selección abierta: añade o quita la tarjeta. */
    fun toggleAlbumSelection(id: String) {
        _state.update {
            val next = if (id in it.selectedAlbumIds) it.selectedAlbumIds - id
            else it.selectedAlbumIds + id
            it.copy(selectedAlbumIds = next)
        }
    }

    /**
     * Seleccionar todo lo VISIBLE (ámbito y búsqueda aplicados); si ya lo
     * está, deselecciona.
     */
    fun toggleSelectAllVisible() {
        _state.update {
            val visible = it.visibleAlbums.mapTo(mutableSetOf()) { a -> a.id }
            val allSelected = visible.isNotEmpty() && it.selectedAlbumIds.containsAll(visible)
            it.copy(selectedAlbumIds = if (allSelected) emptySet() else visible)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedAlbumIds = emptySet()) }
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
                            selectedAlbumIds = it.selectedAlbumIds.filterTo(mutableSetOf()) { id ->
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
                            error = errorFactory.from(error, "No se pudieron cargar los álbumes")
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
                            error = errorFactory.from(error, "No se pudo crear el álbum")
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
                            albums = previous.albums.map {
                                if (it.id == updated.id) updated.keepingPinOf(it) else it
                            },
                            isMutating = false,
                            selectedAlbumIds = emptySet()
                        )
                    }
                    onSuccess(updated)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo renombrar el álbum")
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
                            selectedAlbumIds = emptySet()
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo eliminar el álbum")
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
                            selectedAlbumIds = emptySet()
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo salir del álbum")
                        )
                    }
                }
        }
    }

    /**
     * Borra los álbumes seleccionados repitiendo la llamada de uno solo. Los
     * que fallan siguen seleccionados para reintentar; los borrados salen de
     * la lista. [onResult] recibe el recuento para el snackbar.
     */
    fun deleteSelected(onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit) =
        runOnSelection(onResult) { id -> repository.delete(id) }

    /** Como [deleteSelected], pero saliendo de álbumes que me han compartido. */
    fun leaveSelected(onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit) =
        runOnSelection(onResult) { id -> repository.leave(id) }

    private fun runOnSelection(
        onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit,
        action: suspend (String) -> Unit,
    ) {
        val ids = _state.value.selectedAlbums.map { it.id }
        if (ids.isEmpty() || _state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            val outcome = com.photonne.app.ui.selection.runBulk(ids, action = action)
            val removed = outcome.succeeded.toSet()
            _state.update { previous ->
                previous.copy(
                    albums = previous.albums.filterNot { it.id in removed },
                    isMutating = false,
                    selectedAlbumIds = outcome.failed.toSet()
                )
            }
            onResult(outcome)
        }
    }

    fun applyUpdate(updated: AlbumSummary) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == updated.id) updated.keepingPinOf(it) else it
                }
            )
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

/**
 * Renombrar, cambiar portada o editar devuelven el álbum entero, pero ninguna
 * de esas acciones toca el fijado (y un servidor antiguo no lo manda): se
 * conserva el que ya teníamos.
 */
private fun AlbumSummary.keepingPinOf(previous: AlbumSummary): AlbumSummary =
    copy(isPinned = previous.isPinned, pinnedAt = previous.pinnedAt)
