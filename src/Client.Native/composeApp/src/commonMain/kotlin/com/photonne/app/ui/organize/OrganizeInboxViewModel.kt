package com.photonne.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.data.models.OrganizeSuggestion
import com.photonne.app.data.models.OrganizeSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelinePage
import com.photonne.app.data.models.YearGroup
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import com.photonne.app.ui.selection.withSelection
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class OrganizeInboxUiState(
    val items: List<TimelineItem> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true,
    val loaded: Boolean = false,
    /** Current selection grouped by capture year (with ids), for the "se repartirán
     *  en…" chips under the move picker and the "Revisar" grid. */
    val moveYearGroups: List<YearGroup> = emptyList(),
    /** Set after a year-organized move so the UI can show a "repartidas en…"
     *  summary; null when the last move was flat (or none yet). */
    val lastMoveSummary: MoveOutcome? = null,
    /**
     * Total real de la bandeja y tramo de fechas que cubre. El total NO es
     * `items.size`: la rejilla solo tiene lo paginado hasta ahora, y una
     * cabecera que contara eso subiría sola al hacer scroll.
     */
    val summary: OrganizeSummary? = null,
    /** Lotes propuestos por el servidor; vacío mientras cargan o si no hay. */
    val suggestions: List<OrganizeSuggestion> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    /** true cuando el usuario ha pedido ver la rejilla plana completa. */
    val showAllItems: Boolean = false,
    /**
     * El lote abierto: se ve solo (sus fotos, todas marcadas) o dentro de la
     * bandeja entera con la misma selección, para añadirle fotos cercanas.
     */
    val batch: OrganizeSuggestion? = null,
    /** Lo que sigue pendiente del lote, más nuevo primero. */
    val batchItems: List<TimelineItem> = emptyList(),
    val isLoadingBatch: Boolean = false,
    val batchScope: OrganizeBatchScope = OrganizeBatchScope.Batch,
    /** Foto del lote a la que llevar la rejilla al pasar a la bandeja entera;
     *  la pantalla la consume con [OrganizeInboxViewModel.consumeBatchReveal]. */
    val batchRevealId: String? = null,
) {
    /** Pending count = the loaded items; the whole inbox is materialized here as
     *  the user pages, and the header reflects what's visible. The authoritative
     *  badge on the entry point comes from the cheap /count endpoint. */
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isInitialLoading
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
    val isBatchOnly: Boolean get() = batch != null && batchScope == OrganizeBatchScope.Batch
    /** Lo que pinta la rejilla: el lote solo o la bandeja. */
    val gridItems: List<TimelineItem> get() = if (isBatchOnly) batchItems else items
    /** Todo lo cargado, para resolver la selección (que puede venir de las dos). */
    val knownItems: List<TimelineItem>
        get() = if (batch == null) items else (batchItems + items).distinctBy { it.id }
}

enum class OrganizeBatchScope { Batch, Inbox }

/** Páginas de la bandeja que se cargan como mucho para llegar al lote. */
private const val MAX_REVEAL_PAGES = 10

/**
 * Drives the "Para organizar" inbox screen: a paged grid of the assets still
 * under MobileBackup, with multi-select and a move-out action that files them
 * into a folder (which removes them from the inbox).
 */
class OrganizeInboxViewModel(
    private val repository: OrganizeRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizeInboxUiState())
    val state: StateFlow<OrganizeInboxUiState> = _state.asStateFlow()

    /** The inbox page being fetched. A refresh replaces any append in flight,
     *  whose stale cursor would otherwise land on top of the fresh first page. */
    private var pageJob: Job? = null
    private var batchJob: Job? = null
    private var revealJob: Job? = null

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
                isAppending = false,
                error = null
            )
        }
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            // El resumen va aparte y no bloquea: si falla, la bandeja se
            // muestra igual, solo sin la cabecera.
            launch {
                runCatching { repository.summary() }
                    .onSuccess { summary -> _state.update { it.copy(summary = summary) } }
            }
            launch { loadSuggestions() }
            _state.value.batch?.let { batch -> launch { loadBatchItems(batch) } }
            suspendRunCatching { repository.inbox(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        // Lo del lote no tiene por qué estar en la primera página.
                        val known = (page.items + it.batchItems).mapTo(HashSet()) { item -> item.id }
                        val remaining = it.selection.intersect(known)
                        it.copy(
                            items = page.items,
                            selection = remaining,
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
                            error = errorFactory.from(error, "No se pudo cargar la bandeja")
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
        pageJob = viewModelScope.launch {
            suspendRunCatching { repository.inbox(cursor = cursor) }
                .onSuccess(::appendPage)
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudo cargar más")
                        )
                    }
                }
        }
    }

    private fun appendPage(page: TimelinePage) {
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

    fun toggleSelection(assetId: String) {
        _state.update { it.copy(selection = it.selection.toggled(assetId)) }
    }

    /** Un frame de arrastre en banda, en una sola mutación — ver [SelectionPatch]. */
    fun applySelection(patch: SelectionPatch) {
        if (patch.isEmpty) return
        _state.update { it.copy(selection = it.selection.applying(patch)) }
    }

    /** Marca o desmarca [ids] en bloque, sin alternar (carril de filas). */
    fun setSelected(ids: Collection<String>, selected: Boolean) {
        _state.update { it.copy(selection = it.selection.withSelection(ids, selected)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelectAll() {
        _state.update { previous ->
            previous.copy(selection = previous.selection.toggledAll(previous.gridItems.map { it.id }))
        }
    }

    /**
     * Files the selected assets into [targetFolderId] (physical move out of
     * MobileBackup). On success the moved items are dropped from the inbox and
     * [onComplete] receives the moved ids so callers can refresh the entry-point
     * count.
     */
    /** Loads the current selection grouped by capture year so the move picker can
     *  show "se repartirán en…" and the "Revisar" grid while "Organizar por año"
     *  is checked. Server-computed (from CapturedAt) so it matches the real move. */
    fun loadMoveYearBreakdown() {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty()) {
            _state.update { it.copy(moveYearGroups = emptyList()) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.yearBreakdown(ids) }
                .onSuccess { groups -> _state.update { it.copy(moveYearGroups = groups) } }
                // A failed preview shouldn't block the move; just show nothing.
                .onFailure { _state.update { it.copy(moveYearGroups = emptyList()) } }
        }
    }

    /**
     * Aparta lo seleccionado: deja de contar como pendiente sin moverlo ni
     * archivarlo. Es la única forma de que la bandeja llegue a cero cuando hay
     * capturas y memes que nunca se van a guardar en ninguna carpeta.
     *
     * [onDone] recibe los ids para poder ofrecer deshacerlo.
     */
    fun excludeSelected(onDone: (List<String>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        setExcluded(ids, excluded = true) { onDone(ids) }
    }

    /** Devuelve [ids] a la bandeja. Es el deshacer de [excludeSelected]. */
    fun includeAgain(ids: List<String>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) return
        setExcluded(ids, excluded = false) { onDone() }
    }

    private fun setExcluded(ids: List<String>, excluded: Boolean, onDone: () -> Unit) {
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setExcluded(ids, excluded) }
                .onSuccess {
                    val touched = ids.toHashSet()
                    _state.update {
                        it.copy(
                            // Al apartar salen de la bandeja; al devolverlos, el
                            // refresco de abajo los trae de vuelta en su sitio.
                            items = if (excluded) {
                                it.items.filterNot { item -> item.id in touched }
                            } else it.items,
                            batchItems = if (excluded) {
                                it.batchItems.filterNot { item -> item.id in touched }
                            } else it.batchItems,
                            selection = it.selection - touched,
                            isBulkMutating = false,
                        )
                    }
                    if (!excluded) refresh() else {
                        refreshSummary()
                        leaveBatchIfDone()
                    }
                    onDone()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo apartar")
                        )
                    }
                }
        }
    }

    /**
     * Carga los lotes propuestos. Va aparte de la rejilla y no la bloquea: si
     * el agrupado falla, la bandeja se sigue pudiendo usar a mano, que es
     * exactamente lo que había antes.
     */
    private suspend fun loadSuggestions() {
        _state.update { it.copy(isLoadingSuggestions = true) }
        runCatching { repository.suggestions() }
            .onSuccess { list ->
                _state.update { it.copy(suggestions = list, isLoadingSuggestions = false) }
            }
            .onFailure {
                _state.update { it.copy(suggestions = emptyList(), isLoadingSuggestions = false) }
            }
    }

    /**
     * Abre el lote: primero solo, con todas sus fotos marcadas, para ver de un
     * vistazo si el agrupado acierta. Las barras de selección de siempre actúan
     * sobre él; [setBatchScope] lo pone dentro de la bandeja para añadir más.
     */
    fun selectSuggestion(suggestion: OrganizeSuggestion) {
        revealJob?.cancel()
        _state.update {
            it.copy(
                batch = suggestion,
                batchItems = emptyList(),
                isLoadingBatch = true,
                batchScope = OrganizeBatchScope.Batch,
                batchRevealId = null,
                selection = suggestion.assetIds.toSet(),
                showAllItems = true,
            )
        }
        batchJob?.cancel()
        batchJob = viewModelScope.launch { loadBatchItems(suggestion) }
    }

    /**
     * Trae lo que sigue pendiente del lote. Lo que ya no lo está (movido o
     * apartado desde otro sitio) sale también de la selección: si no, se
     * contaría en "N seleccionadas" sin verse en ninguna parte.
     */
    private suspend fun loadBatchItems(suggestion: OrganizeSuggestion) {
        suspendRunCatching { repository.batchItems(suggestion.assetIds) }
            .onSuccess { list ->
                if (_state.value.batch?.key != suggestion.key) return@onSuccess
                val pending = list.mapTo(HashSet()) { it.id }
                val stale = suggestion.assetIds.filterNot { it in pending }.toSet()
                _state.update {
                    it.copy(batchItems = list, isLoadingBatch = false, selection = it.selection - stale)
                }
                leaveBatchIfDone()
            }
            .onFailure { error ->
                if (_state.value.batch?.key != suggestion.key) return@onFailure
                // Un servidor sin el endpoint (o un fallo puntual): el lote se
                // arma con lo que ya hay cargado de la bandeja, sin cambiar de vista.
                val ids = suggestion.assetIds.toSet()
                val local = _state.value.items.filter { item -> item.id in ids }
                if (local.isNotEmpty()) {
                    _state.update { it.copy(batchItems = local, isLoadingBatch = false) }
                    return@onFailure
                }
                // Sin nada que enseñar aparte queda lo de antes: la bandeja con él marcado.
                _state.update {
                    it.copy(
                        isLoadingBatch = false,
                        batchScope = OrganizeBatchScope.Inbox,
                        error = errorFactory.from(error, "No se pudo cargar el lote")
                    )
                }
            }
    }

    /** Lote solo o bandeja entera; al pasar a la bandeja, la rejilla va al lote. */
    fun setBatchScope(scope: OrganizeBatchScope) {
        if (_state.value.batch == null || _state.value.batchScope == scope) return
        _state.update { it.copy(batchScope = scope, batchRevealId = null) }
        if (scope == OrganizeBatchScope.Inbox) revealBatchInInbox()
    }

    /**
     * Carga páginas de la bandeja hasta que aparezca la foto más nueva del lote
     * (con tope: un lote de hace años no debe bajarse la bandeja entera) y la
     * deja en [OrganizeInboxUiState.batchRevealId] para que la rejilla vaya allí.
     */
    private fun revealBatchInInbox() {
        val target = _state.value.batchItems.firstOrNull()?.id ?: return
        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            var pages = 0
            while (_state.value.items.none { it.id == target } && pages < MAX_REVEAL_PAGES) {
                // Una página en vuelo (el scroll de la rejilla) llega primero.
                if (_state.value.isAppending) pageJob?.join()
                val snapshot = _state.value
                if (snapshot.items.any { it.id == target }) break
                if (!snapshot.hasMore || snapshot.isInitialLoading) break
                val cursor = snapshot.nextCursor ?: break
                _state.update { it.copy(isAppending = true) }
                val page = suspendRunCatching { repository.inbox(cursor = cursor) }.getOrNull()
                if (page == null) {
                    _state.update { it.copy(isAppending = false) }
                    break
                }
                appendPage(page)
                pages++
            }
            if (_state.value.items.any { it.id == target }) {
                _state.update { it.copy(batchRevealId = target) }
            }
        }
    }

    fun consumeBatchReveal() {
        _state.update { it.copy(batchRevealId = null) }
    }

    /** Con todo el lote ya movido o apartado no queda nada que ver: a los lotes. */
    private fun leaveBatchIfDone() {
        val snapshot = _state.value
        if (snapshot.batch != null && !snapshot.isLoadingBatch && snapshot.batchItems.isEmpty()) {
            showSuggestions()
        }
    }

    fun showAllItems() {
        _state.update { it.copy(showAllItems = true) }
    }

    fun showSuggestions() {
        batchJob?.cancel()
        revealJob?.cancel()
        _state.update {
            it.copy(
                showAllItems = false,
                selection = emptySet(),
                batch = null,
                batchItems = emptyList(),
                isLoadingBatch = false,
                batchScope = OrganizeBatchScope.Batch,
                batchRevealId = null,
            )
        }
    }

    private fun refreshSummary() {
        viewModelScope.launch {
            runCatching { repository.summary() }
                .onSuccess { summary -> _state.update { it.copy(summary = summary) } }
        }
    }

    /** After photos leave the inbox: the header count and the proposed
     *  batches still counted (and offered) them. */
    private fun refreshSummaryAndSuggestions() {
        refreshSummary()
        viewModelScope.launch { loadSuggestions() }
    }

    fun clearMoveSummary() {
        _state.update { it.copy(lastMoveSummary = null) }
    }

    /**
     * Mueve lo seleccionado, o solo [onlyIds] cuando la revisión ha quitado
     * fotos del lote. Solo se descuenta de la bandeja lo que se mueve: quitar
     * algo en la revisión debe dejarlo AQUÍ, no moverlo igualmente.
     */
    fun moveSelectedAssets(
        targetFolderId: String,
        organizeByYear: Boolean = false,
        onlyIds: List<String>? = null,
        onComplete: (moved: Int) -> Unit = {}
    ) {
        val selected = _state.value.selection
        val ids = onlyIds?.filter { it in selected } ?: selected.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.moveAssets(targetFolderId, ids, organizeByYear) }
                .onSuccess { outcome ->
                    val requested = ids.toHashSet()
                    // Movimiento parcial: no sabemos cuáles se quedaron, así
                    // que la bandeja se recarga en vez de quitarlos todos.
                    val partial = outcome.moved < ids.size
                    _state.update {
                        it.copy(
                            items = if (partial) it.items else it.items.filterNot { item -> item.id in requested },
                            batchItems = if (partial) it.batchItems else {
                                it.batchItems.filterNot { item -> item.id in requested }
                            },
                            // Lo apartado en la revisión sigue seleccionado,
                            // listo para mandarlo a otro sitio sin re-marcarlo.
                            selection = it.selection - requested,
                            isBulkMutating = false,
                            moveYearGroups = emptyList(),
                            lastMoveSummary = outcome.takeIf { o -> o.yearBreakdown.isNotEmpty() },
                        )
                    }
                    if (partial) refresh() else {
                        refreshSummaryAndSuggestions()
                        leaveBatchIfDone()
                    }
                    onComplete(outcome.moved)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudieron mover")
                        )
                    }
                }
        }
    }

    /** Adds the selection to an album. The assets stay in the inbox (adding to an
     *  album doesn't file them out of MobileBackup); we just clear the selection.
     *  [onComplete] receives the added items for local album-count fan-out. */
    fun bulkAddToAlbum(albumId: String, onComplete: (List<TimelineItem>) -> Unit = {}) {
        val items = _state.value.knownItems.filter { it.id in _state.value.selection }
        val ids = items.map { it.id }
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onComplete(items)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    fun bulkArchive(onResult: (UiError?) -> Unit = {}) {
        bulkRemoveLocally("No se pudieron archivar", onResult) { assetRepository.archive(it) }
    }

    fun bulkTrash(onResult: (UiError?) -> Unit = {}) {
        bulkRemoveLocally("No se pudieron eliminar", onResult) { assetRepository.trash(it) }
    }

    /**
     * Archive/trash also file an asset out of the inbox (it's no longer pending),
     * so on success they drop the moved ids locally — same shape as the move.
     */
    private fun bulkRemoveLocally(
        fallbackMessage: String,
        onResult: (UiError?) -> Unit = {},
        action: suspend (List<String>) -> Unit
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess {
                    val moved = ids.toHashSet()
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { item -> item.id in moved },
                            batchItems = it.batchItems.filterNot { item -> item.id in moved },
                            selection = emptySet(),
                            isBulkMutating = false
                        )
                    }
                    refreshSummaryAndSuggestions()
                    leaveBatchIfDone()
                    onResult(null)
                }
                .onFailure { error ->
                    val uiError = errorFactory.from(error, fallbackMessage)
                    _state.update { it.copy(isBulkMutating = false, error = uiError) }
                    onResult(uiError)
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
