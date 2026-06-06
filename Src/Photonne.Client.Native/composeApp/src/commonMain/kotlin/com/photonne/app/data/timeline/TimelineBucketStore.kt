package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineBucket
import com.photonne.app.data.models.TimelineGridItem
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

    /**
     * Whole-library structure index (layout data only), keyed by month.
     * Fetched once in the background; unloaded months render their real
     * justified structure from it instead of square skeletons. Null until
     * fetched; cleared by [refresh].
     */
    private val _gridIndex = MutableStateFlow<Map<String, List<TimelineGridItem>>?>(null)
    val gridIndex: StateFlow<Map<String, List<TimelineGridItem>>?> = _gridIndex.asStateFlow()
    private var gridIndexInFlight = false

    /** Guards [inFlight], [lastTouched], year-fetch state and eviction. */
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    /** Sample size of the cached/in-flight year summaries (0 = none). */
    private var yearSampleSize = 0
    private var yearSampleInFlight = 0

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
            lastTouched.clear()
            _buckets.value = skeleton.map { TimelineBucketState(key = it.key, count = it.count) }
            // Year summaries and the structure index are snapshots of the
            // same library — refetch on demand after a refresh.
            _yearSummaries.value = null
            yearSampleSize = 0
            _gridIndex.value = null
        }
    }

    /**
     * Fetches the whole-library structure index once; concurrent callers
     * dedup. Failures propagate — callers treat the index as an enhancement
     * (square skeletons remain the fallback) and may retry later.
     */
    suspend fun ensureGridIndex() {
        mutex.withLock {
            if (_gridIndex.value != null || gridIndexInFlight) return
            gridIndexInFlight = true
        }
        try {
            // The whole-library index is the heaviest payload the client
            // parses — keep both the deserialization and the map reshaping
            // off the main thread.
            val byMonth = fetchOffMain {
                api.getTimelineGridIndex().associate { it.yearMonth to it.items }
            }
            mutex.withLock { _gridIndex.value = byMonth }
        } finally {
            mutex.withLock { gridIndexInFlight = false }
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
            val summaries = fetchOffMain { api.getTimelineYears(needed) }
            mutex.withLock {
                _yearSummaries.value = summaries
                yearSampleSize = needed
            }
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
                            val items = fetchOffMain { api.getTimelineBucketItems(key) }
                            storeItems(key, items)
                        } finally {
                            mutex.withLock { inFlight -= key }
                        }
                    }
                }
            }
        } finally {
            // Clears the spinner on the keys this call failed to load (its
            // own successes already flipped isLoading in storeItems).
            setLoading(toFetch, loading = false)
        }
        evictBeyondCap()
    }

    /**
     * Removes an asset from its (loaded) bucket and decrements the count so
     * the reserved height tracks reality. Buckets that reach zero stay in
     * the skeleton with count 0 — the grid simply renders nothing for them;
     * the next [refresh] drops them.
     */
    suspend fun removeItem(assetId: String) = removeItems(setOf(assetId))

    /** Bulk variant of [removeItem] — one state pass for the whole set. */
    suspend fun removeItems(assetIds: Collection<String>) {
        if (assetIds.isEmpty()) return
        val ids = assetIds.toSet()
        mutex.withLock {
            _buckets.update { buckets ->
                buckets.map { bucket ->
                    val items = bucket.items ?: return@map bucket
                    val remaining = items.filterNot { it.id in ids }
                    if (remaining.size == items.size) return@map bucket
                    bucket.copy(items = remaining, count = remaining.size)
                }
            }
        }
    }

    /** Applies [transform] to the asset wherever it's loaded (e.g. favorite). */
    suspend fun updateItem(assetId: String, transform: (TimelineItem) -> TimelineItem) {
        mutex.withLock {
            _buckets.update { buckets ->
                buckets.map { bucket ->
                    val items = bucket.items ?: return@map bucket
                    if (items.none { it.id == assetId }) return@map bucket
                    bucket.copy(items = items.map { if (it.id == assetId) transform(it) else it })
                }
            }
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

    private suspend fun storeItems(key: String, items: List<TimelineItem>) {
        mutex.withLock {
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
    }
}
