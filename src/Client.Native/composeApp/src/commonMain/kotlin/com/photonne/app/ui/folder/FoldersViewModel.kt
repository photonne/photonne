package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.writableMoveDestinations
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
import kotlin.time.Clock
import kotlin.time.Instant

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
    // My pinned folders at any depth (a pin can sit on a nested folder), last
    // pinned first. Colecciones mixes them with pinned albums in "Fijados".
    val pinnedFolders: List<FolderSummary> = emptyList(),
    // Live count of unorganized (MobileBackup) assets, shown on the "Para
    // organizar" entry card.
    val organizePendingCount: Int = 0,
    val scope: FoldersScope = FoldersScope.All,
    val sort: FolderSort = FolderSort.Name,
    val direction: SortDirection = SortDirection.Ascending,
    val viewMode: FolderViewMode = FolderViewMode.List,
    // Tarjetas seleccionadas en la lista. Una sola es el caso de siempre
    // (acciones de esa carpeta); varias habilitan las acciones en bloque.
    val selectedFolderIds: Set<String> = emptySet(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    val isSelectionActive: Boolean get() = selectedFolderIds.isNotEmpty()

    /** Las carpetas seleccionadas que siguen existiendo (ver [findFolder]). */
    val selectedFolders: List<FolderSummary>
        get() = selectedFolderIds.mapNotNull { findFolder(it) }

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
     * A selected folder, looked up across every bucket at
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
    mutationBus: com.photonne.app.data.events.AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    init {
        // Punto 52: las mutaciones de assets cambian recuentos (y en el futuro
        // portadas) de carpetas; recarga silenciosa vía bus.
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

    /** Recarga oportunista tras una mutación: sin spinner, fallo en silencio. */
    private fun refreshQuietly() {
        if (allFolders.isEmpty() && !_state.value.isLoading) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { folders ->
                    allFolders = folders
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedFolderIds = it.selectedFolderIds.filterTo(mutableSetOf()) { id ->
                                folders.any { f -> f.id == id }
                            }
                        )
                    }
                    repartition()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
    }

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
            runCatching { organizeRepository.summary() }
                .onSuccess { summary ->
                    _state.update { it.copy(organizePendingCount = summary.count) }
                }
        }
    }

    private fun loadInitialState(): FoldersUiState {
        val sort = readFolderSort(settings)
        val direction = readFolderDirection(settings, sort)
        val viewMode = readFolderViewMode(settings)
        return FoldersUiState(sort = sort, direction = direction, viewMode = viewMode)
    }

    /**
     * Deliberately not persisted, unlike sort/direction/view mode: those are
     * presentational, this one hides folders. A subtractive filter restored
     * weeks later — with only a tinted Tune icon to explain it — reads as
     * missing data.
     */
    fun setScope(scope: FoldersScope) {
        _state.update { it.copy(scope = scope, selectedFolderIds = emptySet()) }
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
        settings.putString(FOLDERS_VIEW_MODE_KEY, mode.name)
        _state.update { it.copy(viewMode = mode) }
    }

    /** Pulsación larga: entra en selección (o añade la tarjeta a la que hay). */
    fun selectFolder(id: String) {
        _state.update { it.copy(selectedFolderIds = it.selectedFolderIds + id) }
    }

    /** Toque con la selección abierta: añade o quita la tarjeta. */
    fun toggleFolderSelection(id: String) {
        _state.update {
            val next = if (id in it.selectedFolderIds) it.selectedFolderIds - id
            else it.selectedFolderIds + id
            it.copy(selectedFolderIds = next)
        }
    }

    /**
     * Seleccionar todo lo VISIBLE (ámbito y búsqueda aplicados); si ya lo
     * está, deselecciona.
     */
    fun toggleSelectAllVisible() {
        _state.update {
            val visible = it.visibleFolders.mapTo(mutableSetOf()) { f -> f.id }
            val allSelected = visible.isNotEmpty() && it.selectedFolderIds.containsAll(visible)
            it.copy(selectedFolderIds = if (allSelected) emptySet() else visible)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFolderIds = emptySet()) }
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
                            selectedFolderIds = it.selectedFolderIds.filterTo(mutableSetOf()) { id ->
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
                            error = errorFactory.from(error, "No se pudieron cargar las carpetas")
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
                            error = errorFactory.from(error, "No se pudo crear la carpeta")
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
                    allFolders = allFolders.map { if (it.id == updated.id) updated.keepingPinOf(it) else it }
                    repartition()
                    _state.update { it.copy(isMutating = false, selectedFolderIds = emptySet()) }
                    onSuccess(updated)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo renombrar la carpeta")
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
                    _state.update { it.copy(isMutating = false, selectedFolderIds = emptySet()) }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo eliminar la carpeta")
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
                    _state.update { it.copy(isMutating = false, selectedFolderIds = emptySet()) }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo actualizar la visibilidad en la línea de tiempo")
                        )
                    }
                }
        }
    }

    /**
     * Manda a la papelera las carpetas seleccionadas con la misma llamada que
     * el borrado de una (su subárbol va con ella). Solo las de más arriba: las
     * anidadas en otra seleccionada ya caen con su padre. Una a una, porque
     * cada borrado mueve ficheros en disco. Las que fallan siguen
     * seleccionadas; [onResult] recibe el recuento para el snackbar.
     */
    fun deleteSelected(onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit) {
        val targets = topmostFolders(_state.value.selectedFolders)
        runOnSelection(targets, onResult, onDone = { outcome ->
            val removed = outcome.succeeded.toSet()
            allFolders = allFolders.filterNot { it.id in removed }
        }) { folder -> repository.delete(folder.id) }
    }

    /**
     * Mueve las carpetas seleccionadas bajo [targetParentId] (null = raíz
     * personal), repitiendo el PUT de una sola carpeta — el servidor no tiene
     * movimiento masivo. Mismas reglas que [deleteSelected].
     */
    fun moveSelected(
        targetParentId: String?,
        onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit
    ) {
        val targets = topmostFolders(_state.value.selectedFolders)
        val moved = mutableMapOf<String, FolderSummary>()
        runOnSelection(targets, onResult, onDone = {
            allFolders = allFolders.map { moved[it.id] ?: it }
        }) { folder ->
            moved[folder.id] = repository.update(
                folderId = folder.id,
                name = folder.name,
                parentFolderId = targetParentId
            )
        }
    }

    private fun runOnSelection(
        targets: List<FolderSummary>,
        onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit,
        onDone: (com.photonne.app.ui.selection.BulkOutcome) -> Unit,
        action: suspend (FolderSummary) -> Unit,
    ) {
        if (targets.isEmpty() || _state.value.isMutating) return
        val byId = targets.associateBy { it.id }
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            val outcome = com.photonne.app.ui.selection.runBulk(
                ids = targets.map { it.id },
                concurrency = 1
            ) { id -> action(byId.getValue(id)) }
            onDone(outcome)
            repartition()
            _state.update {
                it.copy(isMutating = false, selectedFolderIds = outcome.failed.toSet())
            }
            onResult(outcome)
            // Los subárboles cambian de ruta o desaparecen con su padre, y
            // "Para organizar" puede haber perdido fotos: recarga silenciosa.
            if (outcome.succeeded.isNotEmpty()) {
                refreshQuietly()
                refreshOrganizeCount()
            }
        }
    }

    fun applyUpdate(updated: FolderSummary) {
        allFolders = allFolders.map { if (it.id == updated.id) updated.keepingPinOf(it) else it }
        repartition()
    }

    private val pinsInFlight = mutableSetOf<String>()

    /**
     * Fija o desfija [folderId] para mí (optimista), como los álbumes: la
     * tarjeta cambia al instante y, si el servidor falla, vuelve a como estaba
     * y [onFailure] recibe si se intentaba fijar (true) o desfijar (false).
     */
    fun togglePin(folderId: String, onFailure: (pinning: Boolean) -> Unit = {}) {
        val current = allFolders.firstOrNull { it.id == folderId } ?: return
        if (!pinsInFlight.add(folderId)) return
        val pin = !current.isPinned
        setPinned(folderId, pin, if (pin) Clock.System.now() else null)
        viewModelScope.launch {
            try {
                repository.setPinned(folderId, pin)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setPinned(folderId, current.isPinned, current.pinnedAt)
                onFailure(pin)
            } finally {
                pinsInFlight.remove(folderId)
            }
        }
    }

    private fun setPinned(folderId: String, pinned: Boolean, pinnedAt: Instant?) {
        allFolders = allFolders.map {
            if (it.id == folderId) it.copy(isPinned = pinned, pinnedAt = pinnedAt) else it
        }
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
                moveDestinations = sorted(writableMoveDestinations(allFolders, username)),
                pinnedFolders = allFolders.filter { f -> f.isPinned }.sortedByDescending { f -> f.pinnedAt }
            )
        }
    }
}


/**
 * Renombrar devuelve la carpeta entera, pero no toca el fijado (y un servidor
 * antiguo no lo manda): se conserva el que ya teníamos.
 */
private fun FolderSummary.keepingPinOf(previous: FolderSummary): FolderSummary =
    copy(isPinned = previous.isPinned, pinnedAt = previous.pinnedAt)
