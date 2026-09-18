package com.photonne.app.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.ExifData
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PersonAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val detail: AssetDetail? = null,
    val isLoading: Boolean = false,
    val error: UiError? = null,
    // Lazily-loaded extras for the info panel, keyed implicitly to [detail].
    val faces: List<Face> = emptyList(),
    /** False while the faces request is in flight: an empty [faces] then means
     *  "not known yet", not "no faces". */
    val facesLoaded: Boolean = false,
    /** The faces request failed; the panel falls back to the sheet entry. */
    val facesFailed: Boolean = false,
    val samePersonAssets: List<PersonAsset> = emptyList(),
    val sameDayAssets: List<PersonAsset> = emptyList(),
)

/**
 * Holds the metadata for the *currently visible* asset in the pager.
 * Detail is fetched lazily and cached per asset id while the screen is
 * alive. Caller invokes [select] every time the visible page changes.
 */
class AssetDetailViewModel(
    private val repository: AssetDetailRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val cache = mutableMapOf<String, AssetDetail>()
    // Snapshot of the cache, exposed so the pager can show prev/next assets'
    // metadata immediately (prefetched) instead of waiting for them to become
    // the selected page.
    private val _details = MutableStateFlow<Map<String, AssetDetail>>(emptyMap())
    val details: StateFlow<Map<String, AssetDetail>> = _details.asStateFlow()
    private var currentJob: Job? = null
    private var currentId: String? = null

    /** Info-panel extras (faces + related assets), cached per asset id for the
     *  lifetime of the screen so swiping back is instant. Each piece is null
     *  until its own request succeeds, so a failed or cancelled load is
     *  retried instead of being remembered as "empty". */
    private data class Extras(
        val faces: List<Face>? = null,
        val samePersonAssets: List<PersonAsset>? = null,
        val sameDayAssets: List<PersonAsset>? = null,
    )
    private val extrasCache = mutableMapOf<String, Extras>()
    private var extrasJob: Job? = null
    private var facesRefreshJob: Job? = null

    private fun publishCache() {
        _details.value = cache.toMap()
    }

    /**
     * Warms the cache for [assetIds] (e.g. the pager neighbours) so their
     * metadata is ready when the user swipes to them. Skips local-only ids and
     * anything already cached or in flight.
     */
    fun prefetch(assetIds: List<String>) {
        assetIds.forEach { id ->
            if (id.startsWith("device:") || cache.containsKey(id)) return@forEach
            viewModelScope.launch {
                runCatching { repository.getDetail(id) }
                    .onSuccess { detail ->
                        cache[id] = detail
                        publishCache()
                    }
            }
        }
    }

    fun select(assetId: String) {
        if (assetId == currentId && _state.value.detail?.id == assetId) return
        currentId = assetId
        // Synthetic ids ("device:<uri>") represent local-only entries
        // that have no server detail to fetch. Skip the round-trip and
        // let callers fall back to the local TimelineItem fields.
        if (assetId.startsWith("device:")) {
            currentJob?.cancel()
            _state.value = AssetDetailUiState()
            return
        }
        cache[assetId]?.let { cached ->
            _state.value = AssetDetailUiState(detail = cached)
            loadExtras(assetId)
            return
        }
        currentJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null, detail = null) }
        currentJob = viewModelScope.launch {
            runCatching { repository.getDetail(assetId) }
                .onSuccess { detail ->
                    cache[assetId] = detail
                    publishCache()
                    if (currentId == assetId) {
                        _state.value = AssetDetailUiState(detail = detail)
                        loadExtras(assetId)
                    }
                }
                .onFailure { error ->
                    if (currentId == assetId) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = errorFactory.from(error, "Error al cargar los detalles")
                            )
                        }
                    }
                }
        }
    }

    /**
     * Loads the info-panel extras for [assetId]: detected faces, "same people"
     * (assets of the first confirmed person in this asset) and "same day".
     * Each piece is published as soon as it arrives — faces never wait for the
     * related strips. Served from cache when warm; never blocks the detail.
     * Related strips degrade to empty on failure; a faces failure is flagged
     * so the panel can still offer the faces sheet.
     */
    private fun loadExtras(assetId: String) {
        if (assetId.startsWith("device:")) return
        extrasJob?.cancel()
        facesRefreshJob?.cancel()
        val cached = extrasCache[assetId] ?: Extras()
        applyExtras(assetId, cached)
        if (cached.faces != null && cached.samePersonAssets != null && cached.sameDayAssets != null) return
        extrasJob = viewModelScope.launch {
            if (cached.faces == null || cached.samePersonAssets == null) {
                launch { loadFacesAndSamePerson(assetId, cached.faces) }
            }
            if (cached.sameDayAssets == null) launch {
                val sameDay = fetchOrNull { repository.getSameDay(assetId, limit = 12) }
                    ?.items?.filter { it.id != assetId }?.take(8)
                if (sameDay != null) updateExtras(assetId) { it.copy(sameDayAssets = sameDay) }
            }
        }
    }

    /**
     * Drops what's cached for [assetId] and loads it again: detail (tags,
     * description) and every extra. For after an AI analysis finished on the
     * server, when the cached copy is exactly what's stale.
     */
    fun reload(assetId: String) {
        if (assetId.startsWith("device:")) return
        cache.remove(assetId)
        extrasCache.remove(assetId)
        publishCache()
        if (currentId == assetId) {
            currentId = null
            select(assetId)
        }
    }

    /**
     * Re-reads the faces of [assetId] after the faces sheet may have changed
     * them (assign / reject / unassign), along with the "same people" strip
     * that hangs off them. The current strip stays on screen meanwhile.
     */
    fun refreshFaces(assetId: String) {
        if (assetId.startsWith("device:")) return
        val fresh = extrasCache[assetId]?.copy(faces = null, samePersonAssets = null)
        if (fresh != null) extrasCache[assetId] = fresh
        if (currentId != assetId) return
        // Own job: cancelling extrasJob would also drop an in-flight "same day".
        facesRefreshJob?.cancel()
        facesRefreshJob = viewModelScope.launch { loadFacesAndSamePerson(assetId, knownFaces = null) }
    }

    private suspend fun loadFacesAndSamePerson(assetId: String, knownFaces: List<Face>?) {
        val faces = knownFaces ?: fetchOrNull { repository.getFaces(assetId) }
        if (faces == null) {
            // Keep whatever is already on screen (a refresh); only flag the
            // failure when there is nothing to show.
            _state.update {
                if (it.detail?.id == assetId && !it.facesLoaded) it.copy(facesFailed = true) else it
            }
            return
        }
        updateExtras(assetId) { it.copy(faces = faces) }
        val personId = faces.firstOrNull { it.personId != null && !it.isRejected }?.personId
        val samePerson = if (personId != null) {
            fetchOrNull { repository.getPersonAssets(personId, limit = 12) }
                ?.items?.filter { it.id != assetId }?.take(8)
        } else emptyList()
        if (samePerson != null) updateExtras(assetId) { it.copy(samePersonAssets = samePerson) }
    }

    /** Null on failure; cancellation propagates so a cancelled load is never
     *  mistaken for an empty result. */
    private suspend fun <T> fetchOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun updateExtras(assetId: String, transform: (Extras) -> Extras) {
        val updated = transform(extrasCache[assetId] ?: Extras())
        extrasCache[assetId] = updated
        if (currentId == assetId) applyExtras(assetId, updated)
    }

    private fun applyExtras(assetId: String, extras: Extras) {
        _state.update { current ->
            if (current.detail?.id == assetId) {
                current.copy(
                    faces = extras.faces.orEmpty(),
                    facesLoaded = extras.faces != null,
                    facesFailed = false,
                    samePersonAssets = extras.samePersonAssets.orEmpty(),
                    sameDayAssets = extras.sameDayAssets.orEmpty(),
                )
            } else current
        }
    }

    /**
     * Adds a user tag to [assetId] optimistically, then reconciles the
     * tag lists with the server's authoritative (merged) list. Reverts on
     * failure.
     */
    fun addTag(assetId: String, raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        val snapshot = currentDetail(assetId) ?: return
        if (snapshot.userTags.any { it.equals(tag, ignoreCase = true) }) return
        updateTags(assetId, snapshot.tags + tag, snapshot.userTags + tag)
        viewModelScope.launch {
            runCatching { repository.addTags(assetId, listOf(tag)) }
                .onSuccess { merged -> reconcileTags(assetId, merged) }
                .onFailure { error ->
                    updateTags(assetId, snapshot.tags, snapshot.userTags)
                    _state.update { it.copy(error = errorFactory.from(error, "No se pudo añadir la etiqueta")) }
                }
        }
    }

    /** Removes a user tag from [assetId] optimistically, reverting on failure. */
    fun removeTag(assetId: String, tag: String) {
        val snapshot = currentDetail(assetId) ?: return
        if (snapshot.userTags.none { it.equals(tag, ignoreCase = true) }) return
        fun List<String>.without() = filterNot { it.equals(tag, ignoreCase = true) }
        updateTags(assetId, snapshot.tags.without(), snapshot.userTags.without())
        viewModelScope.launch {
            runCatching { repository.removeTag(assetId, tag) }
                .onSuccess { merged -> reconcileTags(assetId, merged) }
                .onFailure { error ->
                    updateTags(assetId, snapshot.tags, snapshot.userTags)
                    _state.update { it.copy(error = errorFactory.from(error, "No se pudo quitar la etiqueta")) }
                }
        }
    }

    private fun currentDetail(assetId: String): AssetDetail? =
        cache[assetId] ?: _state.value.detail?.takeIf { it.id == assetId }

    /** Splits the server's merged tag list back into user vs auto using the
     *  asset's known auto tags, then writes both lists to cache + state. */
    private fun reconcileTags(assetId: String, merged: List<String>) {
        val auto = currentDetail(assetId)?.autoTags ?: emptyList()
        val userTags = merged.filterNot { m -> auto.any { it.equals(m, ignoreCase = true) } }
        updateTags(assetId, merged, userTags)
    }

    private fun updateTags(assetId: String, tags: List<String>, userTags: List<String>) {
        fun AssetDetail.applied() = copy(tags = tags, userTags = userTags)
        cache[assetId]?.let { cache[assetId] = it.applied(); publishCache() }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) current.copy(detail = detail.applied())
            else current
        }
    }

    /**
     * Optimistically flips the favorite state for [assetId] both in the
     * cache and in the visible UI state, then calls the API. On failure
     * the state is reverted. The confirmed value is delivered through
     * [onCommitted] so callers (e.g. App) can propagate it to the
     * timeline grid.
     */
    fun toggleFavorite(assetId: String, onCommitted: (Boolean) -> Unit) {
        val previous = cache[assetId]?.isFavorite
            ?: _state.value.detail?.takeIf { it.id == assetId }?.isFavorite
            ?: false
        val optimistic = !previous

        applyFavorite(assetId, optimistic)

        viewModelScope.launch {
            runCatching { repository.toggleFavorite(assetId) }
                .onSuccess { confirmed ->
                    if (confirmed != optimistic) applyFavorite(assetId, confirmed)
                    onCommitted(confirmed)
                }
                .onFailure { error ->
                    applyFavorite(assetId, previous)
                    _state.update {
                        it.copy(error = errorFactory.from(error, "No se pudo actualizar el favorito"))
                    }
                }
        }
    }

    private fun applyFavorite(assetId: String, isFavorite: Boolean) {
        cache[assetId]?.let { cache[assetId] = it.copy(isFavorite = isFavorite); publishCache() }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(isFavorite = isFavorite))
            } else current
        }
    }

    fun archive(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.archive(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    publishCache()
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, ErrorMessages.ARCHIVE_FAILED))
                    }
                }
        }
    }

    fun trash(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.trash(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    publishCache()
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, ErrorMessages.TRASH_FAILED))
                    }
                }
        }
    }

    fun updateDescription(assetId: String, description: String?) {
        val cleaned = description?.trim()?.takeIf { it.isNotEmpty() }
        // Optimistic local update — both cache and visible state.
        cache[assetId]?.let { cache[assetId] = it.copy(caption = cleaned); publishCache() }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(caption = cleaned))
            } else current
        }
        viewModelScope.launch {
            runCatching { repository.updateDescription(assetId, cleaned) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "No se pudo actualizar la descripción"))
                    }
                }
        }
    }

    /**
     * Asks the server what date it could recover for [assetId] (EXIF re-read
     * from the file and/or inferred from name/folder) WITHOUT applying it.
     * Returns null on failure — the sheet shows a "nothing found" hint.
     */
    suspend fun captureDateSuggestion(assetId: String): com.photonne.app.data.models.CaptureDateSuggestion? =
        runCatching { repository.getCaptureDateSuggestion(assetId) }.getOrNull()

    /**
     * Overrides the capture date of [assetId]. Updates the visible EXIF date
     * optimistically, then reconciles with the server's authoritative value
     * (it persists UTC and may differ by sub-second). When [writeToFile] is
     * true the server also writes the date into the physical file's EXIF.
     */
    fun updateCaptureDate(assetId: String, dateTaken: Instant, writeToFile: Boolean) {
        fun withDate(d: AssetDetail, date: Instant): AssetDetail =
            d.copy(exif = (d.exif ?: ExifData()).copy(dateTaken = date))

        // Optimistic local update — both cache and visible state.
        cache[assetId]?.let { cache[assetId] = withDate(it, dateTaken); publishCache() }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) current.copy(detail = withDate(detail, dateTaken))
            else current
        }
        viewModelScope.launch {
            runCatching { repository.updateCaptureDate(assetId, dateTaken, writeToFile) }
                .onSuccess { resp ->
                    val confirmed = resp.dateTaken ?: return@onSuccess
                    cache[assetId]?.let { cache[assetId] = withDate(it, confirmed); publishCache() }
                    _state.update { current ->
                        val detail = current.detail
                        if (detail != null && detail.id == assetId) current.copy(detail = withDate(detail, confirmed))
                        else current
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "No se pudo actualizar la fecha de captura"))
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
