package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineBucket
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelineYearSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Per-month state of the bucket timeline. [count] is always known (it comes
 * with the skeleton); [items] is null until the bucket's content has been
 * fetched — the grid renders a fixed-height skeleton from [count] meanwhile.
 */
data class TimelineBucketState(
    /** Calendar month key, "yyyy-MM". */
    val key: String,
    val count: Int,
    val items: List<TimelineItem>? = null,
    val isLoading: Boolean = false
) {
    val isLoaded: Boolean get() = items != null
}

/**
 * Client-side store for the bucket timeline (docs/timeline-buckets.md).
 *
 * Owns the skeleton (`/timeline/buckets`) plus an LRU-bounded cache of
 * loaded bucket contents (`/timeline/buckets/{key}`). The ViewModel calls
 * [ensureLoaded] with the keys near the viewport; the store dedups in-flight
 * fetches and evicts content (never counts) for the months touched longest
 * ago once more than [maxLoadedBuckets] are resident, so a fast scrub
 * through years of history doesn't accumulate the whole library in memory.
 */
class TimelineBucketStore(
    private val api: PhotonneApi,
    private val maxLoadedBuckets: Int = DEFAULT_MAX_LOADED_BUCKETS
) {
    private val _buckets = MutableStateFlow<List<TimelineBucketState>>(emptyList())
    val buckets: StateFlow<List<TimelineBucketState>> = _buckets.asStateFlow()

    /**
     * Compressed yearly view: per-year counts + evenly-sampled items, newest
     * first. Null until first fetched; cleared by [refresh].
     */
    private val _yearSummaries = MutableStateFlow<List<TimelineYearSummary>?>(null)
    val yearSummaries: StateFlow<List<TimelineYearSummary>?> = _yearSummaries.asStateFlow()

    /** Guards [inFlight], [lastTouched], year-fetch state and eviction. */
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    /** Sample size of the cached/in-flight year summaries (0 = none). */
    private var yearSampleSize = 0
    private var yearSampleInFlight = 0

    /**
     * Bumped (under [mutex]) by every local mutation of the store's state —
     * [refresh], [reloadBuckets], [removeItems], [updateItem]. A bucket or
     * year fetch captures it before hitting the network and only stores its
     * response if nothing changed meanwhile: otherwise a month requested
     * before a trash would land after it and resurrect the trashed photo
     * (or undo a favorite toggle). A stale response is re-fetched instead
     * of dropped, so the key stays in [inFlight] until fresh data lands.
     */
    private var generation = 0L

    /**
     * Month each asset dropped by [removeItems] lived in (insertion order,
     * trimmed to [MAX_REMEMBERED_REMOVALS]) — see [monthsOfRemoved].
     */
    private val removedFrom = LinkedHashMap<String, String>()

    /** Monotonic recency stamps for LRU eviction. */
    private val lastTouched = mutableMapOf<String, Long>()
    private var touchCounter = 0L

    /**
     * Re-fetches the skeleton. Loaded contents are dropped — refresh means
     * "show me the truth", and the UI's visibility effect re-requests the
     * on-screen buckets immediately, so staleness never survives a refresh.
     */
    suspend fun refresh() {
        val skeleton = fetchOffMain { api.getTimelineBuckets() }
        mutex.withLock {
            generation++
            lastTouched.clear()
            _buckets.value = skeleton.map { TimelineBucketState(key = it.key, count = it.count) }
            // Year summaries are a snapshot of the same library — refetch on
            // demand after a refresh.
            _yearSummaries.value = null
            yearSampleSize = 0
        }
    }

    /**
     * Forgets everything loaded: skeleton, month contents and year summaries.
     * The store is a process-wide singleton, so without this the next account
     * (or server) would see the previous one's grid until its first refresh
     * lands, and keep seeing it if that refresh fails. Bumping [generation]
     * makes any fetch still in flight for the old session drop its response.
     */
    suspend fun clear() {
        mutex.withLock {
            generation++
            lastTouched.clear()
            removedFrom.clear()
            _buckets.value = emptyList()
            _yearSummaries.value = null
            yearSampleSize = 0
        }
    }

    /**
     * Fetches the compressed yearly view with at least [sample] items per
     * year. Cached summaries with an equal-or-larger sample are reused
     * (callers truncate client-side); a smaller cache triggers a refetch.
     * Concurrent calls dedup against the in-flight sample size.
     */
    suspend fun ensureYearSummaries(sample: Int) {
        val needed = sample.coerceIn(1, MAX_YEAR_SAMPLE)
        mutex.withLock {
            val cached = _yearSummaries.value != null && yearSampleSize >= needed
            val inFlightCovers = yearSampleInFlight >= needed
            if (cached || inFlightCovers) return
            yearSampleInFlight = needed
        }
        try {
            fetchUntilCurrent(
                fetch = { api.getTimelineYears(needed) },
                store = { summaries ->
                    _yearSummaries.value = summaries
                    yearSampleSize = needed
                }
            )
        } finally {
            mutex.withLock {
                if (yearSampleInFlight == needed) yearSampleInFlight = 0
            }
        }
    }

    /**
     * Loads the given buckets' contents (concurrently) unless already loaded
     * or in flight. Also refreshes the keys' LRU recency, so calling this
     * with the visible keys both loads and protects them from eviction.
     * Unknown keys are ignored. Cancelling the caller cancels its fetches.
     */
    suspend fun ensureLoaded(keys: List<String>) = load(keys, force = false)

    /**
     * [ensureLoaded]'s body. With [force], loaded keys are fetched again too
     * (their current content stays on screen until the fresh one lands);
     * keys already in flight are still skipped — the generation bump of
     * whoever forced the reload makes that fetch go round again.
     */
    private suspend fun load(keys: List<String>, force: Boolean) {
        val toFetch = mutex.withLock {
            val known = _buckets.value.associateBy { it.key }
            keys.forEach { key -> if (known.containsKey(key)) touch(key) }
            keys.filter { key ->
                val bucket = known[key] ?: return@filter false
                (force || !bucket.isLoaded) && key !in inFlight
            }.also { inFlight += it }
        }
        if (toFetch.isEmpty()) return

        setLoading(toFetch, loading = true)
        try {
            coroutineScope {
                toFetch.forEach { key ->
                    launch {
                        try {
                            fetchUntilCurrent(
                                fetch = { api.getTimelineBucketItems(key) },
                                store = { items -> storeItemsLocked(key, items) }
                            )
                        } finally {
                            mutex.withLock { inFlight -= key }
                        }
                    }
                }
            }
        } finally {
            // Clears the spinner on the keys this call failed to load (its
            // own successes already flipped isLoading in storeItemsLocked).
            setLoading(toFetch, loading = false)
        }
        evictBeyondCap(protected = keys.toSet())
    }

    /**
     * Recarga solo [keys] (los meses de origen y destino de una foto cuya
     * fecha ha cambiado, o los de unas fotos restauradas con Deshacer): trae
     * el esqueleto de nuevo para los recuentos —el destino puede ser un mes
     * que no existía— pero conserva el contenido cargado del resto de meses,
     * a diferencia de [refresh]. Los meses recargados que ya estaban cargados
     * siguen en pantalla hasta que llega su contenido nuevo (sin parpadeo de
     * esqueleto), y la vista por años, si estaba pedida, se vuelve a pedir
     * con el mismo tamaño de muestra en vez de quedarse vacía.
     */
    suspend fun reloadBuckets(keys: Set<String>) {
        if (keys.isEmpty()) return
        val skeleton = fetchOffMain { api.getTimelineBuckets() }
        val yearSample = mutex.withLock {
            generation++
            val previous = _buckets.value.associateBy { it.key }
            _buckets.value = skeleton.map { entry ->
                val prev = previous[entry.key]
                when {
                    prev == null -> TimelineBucketState(key = entry.key, count = entry.count)
                    entry.key !in keys -> prev
                    // Stale but on screen: keep it until the forced fetch lands.
                    prev.isLoaded -> prev
                    else -> TimelineBucketState(key = entry.key, count = entry.count)
                }
            }
            // La vista por años es una muestra de la misma biblioteca: se
            // marca para volver a pedirla, pero lo pintado se queda mientras.
            yearSampleSize.also { yearSampleSize = 0 }
        }
        load(keys.toList(), force = true)
        if (yearSample > 0) ensureYearSummaries(yearSample)
    }

    /**
     * Content of every month in [keys]: loaded months straight from memory,
     * the rest fetched ([CONTENTS_PARALLELISM] at a time) WITHOUT storing
     * them — an operation spanning many months (Shift+click range selection)
     * would otherwise push the visible months out of the LRU cap. Unknown
     * keys are left out. A failed fetch propagates.
     */
    suspend fun contentsOf(keys: List<String>): Map<String, List<TimelineItem>> {
        val known = _buckets.value.associateBy { it.key }
        val loaded = keys.mapNotNull { key -> known[key]?.items?.let { key to it } }.toMap()
        val missing = keys.filter { it in known && it !in loaded }
        if (missing.isEmpty()) return loaded
        val gate = Semaphore(CONTENTS_PARALLELISM)
        val fetched = coroutineScope {
            missing.map { key ->
                async { gate.withPermit { key to fetchOffMain { api.getTimelineBucketItems(key) } } }
            }.awaitAll()
        }
        return loaded + fetched
    }

    /**
     * Months the given assets were removed from by [removeItems], or null if
     * any of them is unknown (it was never loaded here, or the memory was
     * trimmed). Lets a restore (Undo) reload just those months.
     */
    suspend fun monthsOfRemoved(assetIds: Collection<String>): Set<String>? = mutex.withLock {
        assetIds.mapTo(HashSet()) { id -> removedFrom[id] ?: return@withLock null }
    }

    /**
     * Removes an asset from its (loaded) bucket and decrements the count so
     * the reserved height tracks reality. Buckets that reach zero stay in
     * the skeleton with count 0 — the grid simply renders nothing for them;
     * the next [refresh] drops them.
     */
    suspend fun removeItem(assetId: String) = removeItems(setOf(assetId))

    /**
     * Bulk variant of [removeItem] — one state pass for the whole set. Also
     * prunes the year summaries: sampled items go, and each year's count
     * drops by the removed assets we can place in it (via their loaded
     * month or the sample). An asset found in neither leaves its year's
     * count one high until the next refetch — there's no way to know which
     * year it belonged to.
     */
    suspend fun removeItems(assetIds: Collection<String>) {
        if (assetIds.isEmpty()) return
        val ids = assetIds.toSet()
        mutex.withLock {
            generation++
            val removedPerYear = mutableMapOf<Int, MutableSet<String>>()
            _buckets.update { buckets ->
                buckets.map { bucket ->
                    val items = bucket.items ?: return@map bucket
                    val remaining = items.filterNot { it.id in ids }
                    if (remaining.size == items.size) return@map bucket
                    val removed = items.filter { it.id in ids }
                    removed.forEach { removedFrom[it.id] = bucket.key }
                    bucket.key.substringBefore('-').toIntOrNull()?.let { year ->
                        removed.mapTo(removedPerYear.getOrPut(year) { mutableSetOf() }) { it.id }
                    }
                    bucket.copy(items = remaining, count = remaining.size)
                }
            }
            while (removedFrom.size > MAX_REMEMBERED_REMOVALS) {
                removedFrom.remove(removedFrom.keys.first())
            }
            _yearSummaries.update { summaries ->
                summaries?.map { summary ->
                    val removed = removedPerYear[summary.year].orEmpty() +
                        summary.items.filter { it.id in ids }.map { it.id }
                    if (removed.isEmpty()) summary
                    else summary.copy(
                        count = (summary.count - removed.size).coerceAtLeast(0),
                        items = summary.items.filterNot { it.id in ids }
                    )
                }
            }
        }
    }

    /**
     * Applies [transform] to the asset wherever it's loaded (e.g. favorite),
     * year-view samples included.
     */
    suspend fun updateItem(assetId: String, transform: (TimelineItem) -> TimelineItem) {
        mutex.withLock {
            generation++
            _buckets.update { buckets ->
                buckets.map { bucket ->
                    val items = bucket.items ?: return@map bucket
                    if (items.none { it.id == assetId }) return@map bucket
                    bucket.copy(items = items.map { if (it.id == assetId) transform(it) else it })
                }
            }
            _yearSummaries.update { summaries ->
                summaries?.map { summary ->
                    if (summary.items.none { it.id == assetId }) summary
                    else summary.copy(
                        items = summary.items.map { if (it.id == assetId) transform(it) else it }
                    )
                }
            }
        }
    }

    /**
     * Fetches with [fetch] and hands the result to [store] (under [mutex])
     * only if no local mutation bumped [generation] while the request was
     * out; otherwise fetches again. After [MAX_STALE_RETRIES] stale rounds
     * the last response is stored anyway — a mutation storm shouldn't leave
     * the month as a skeleton forever, and the bus re-applies confirmed
     * removals on top.
     */
    private suspend fun <T> fetchUntilCurrent(
        fetch: suspend () -> T,
        store: (T) -> Unit
    ) {
        var attempt = 0
        while (true) {
            val startedAt = mutex.withLock { generation }
            val result = fetchOffMain { fetch() }
            val stored = mutex.withLock {
                if (generation == startedAt || attempt >= MAX_STALE_RETRIES) {
                    store(result)
                    true
                } else false
            }
            if (stored) return
            attempt++
        }
    }

    /**
     * Runs a fetch on [Dispatchers.Default]: Ktor deserializes response
     * bodies in the CALLER's context, and the store is driven from
     * viewModelScope (main). Without this, parsing a dense month or the
     * whole-library index janks the UI thread mid-scroll.
     */
    private suspend fun <T> fetchOffMain(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { block() }

    private fun touch(key: String) {
        lastTouched[key] = ++touchCounter
    }

    private suspend fun setLoading(keys: List<String>, loading: Boolean) {
        mutex.withLock {
            _buckets.update { buckets ->
                buckets.map { bucket ->
                    if (bucket.key in keys && !bucket.isLoaded) bucket.copy(isLoading = loading)
                    else bucket
                }
            }
        }
    }

    /** Caller holds [mutex] — see [fetchUntilCurrent]. */
    private fun storeItemsLocked(key: String, items: List<TimelineItem>) {
        touch(key)
        _buckets.update { buckets ->
            buckets.map { bucket ->
                if (bucket.key == key) {
                    // The server's count is authoritative at skeleton
                    // time, but the content fetch is newer — trust it.
                    bucket.copy(items = items, count = items.size, isLoading = false)
                } else bucket
            }
        }
    }

    /**
     * Drops the least recently touched contents beyond [maxLoadedBuckets],
     * never the [protected] keys the caller just asked for (the viewport and
     * its neighbours). When more months than the cap fit on screen (sparse
     * months on a tall iPad or desktop window), evicting visible ones changed
     * the grid's layout key, the visibility effect asked for them again and
     * the two chased each other forever: requests non-stop and months
     * flickering between skeleton and content. The cap then simply stretches
     * to what is on screen.
     */
    private suspend fun evictBeyondCap(protected: Set<String>) {
        mutex.withLock {
            val loaded = _buckets.value.filter { it.isLoaded }
            val excess = loaded.size - maxLoadedBuckets
            if (excess <= 0) return

            val evictKeys = loaded
                .filter { it.key !in inFlight && it.key !in protected }
                .sortedBy { lastTouched[it.key] ?: 0L }
                .take(excess)
                .mapTo(HashSet()) { it.key }
            if (evictKeys.isEmpty()) return

            _buckets.update { buckets ->
                buckets.map { bucket ->
                    if (bucket.key in evictKeys) bucket.copy(items = null) else bucket
                }
            }
        }
    }

    companion object {
        /**
         * ~1 year of resident months. At a typical 100-1000 items/month this
         * bounds memory while keeping pinch-zoom round trips (Month ↔ Year)
         * cache-warm.
         */
        const val DEFAULT_MAX_LOADED_BUCKETS = 12

        /** Mirrors the server-side clamp on /timeline/years?sample. */
        const val MAX_YEAR_SAMPLE = 100

        /** Re-fetches of a response made stale by a concurrent mutation. */
        private const val MAX_STALE_RETRIES = 3

        /** Concurrent month fetches of [contentsOf]. */
        private const val CONTENTS_PARALLELISM = 4

        /** Removals remembered for [monthsOfRemoved] (an Undo is recent). */
        private const val MAX_REMEMBERED_REMOVALS = 2000
    }
}
