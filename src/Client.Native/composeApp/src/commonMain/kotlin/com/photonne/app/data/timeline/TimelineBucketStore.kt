package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineBucket
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelineYearSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    suspend fun ensureLoaded(keys: List<String>) {
        val toFetch = mutex.withLock {
            val known = _buckets.value.associateBy { it.key }
            keys.forEach { key -> if (known.containsKey(key)) touch(key) }
            keys.filter { key ->
                val bucket = known[key] ?: return@filter false
                !bucket.isLoaded && key !in inFlight
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
        evictBeyondCap()
    }

    /**
     * Recarga solo [keys] (p. ej. los meses de origen y destino de una foto
     * cuya fecha ha cambiado): trae el esqueleto de nuevo para los recuentos
     * —el destino puede ser un mes que no existía— pero conserva el contenido
     * cargado del resto de meses, a diferencia de [refresh].
     */
    suspend fun reloadBuckets(keys: Set<String>) {
        if (keys.isEmpty()) return
        val skeleton = fetchOffMain { api.getTimelineBuckets() }
        mutex.withLock {
            generation++
            val previous = _buckets.value.associateBy { it.key }
            _buckets.value = skeleton.map { entry ->
                val prev = previous[entry.key]
                if (prev != null && entry.key !in keys) prev
                else TimelineBucketState(key = entry.key, count = entry.count)
            }
            keys.forEach { lastTouched.remove(it) }
            // La vista por años es una muestra de la misma biblioteca.
            _yearSummaries.value = null
            yearSampleSize = 0
        }
        ensureLoaded(keys.toList())
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
                    bucket.key.substringBefore('-').toIntOrNull()?.let { year ->
                        items.filter { it.id in ids }
                            .mapTo(removedPerYear.getOrPut(year) { mutableSetOf() }) { it.id }
                    }
                    bucket.copy(items = remaining, count = remaining.size)
                }
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

    private suspend fun evictBeyondCap() {
        mutex.withLock {
            val loaded = _buckets.value.filter { it.isLoaded }
            val excess = loaded.size - maxLoadedBuckets
            if (excess <= 0) return

            val evictKeys = loaded
                .filter { it.key !in inFlight }
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
    }
}
