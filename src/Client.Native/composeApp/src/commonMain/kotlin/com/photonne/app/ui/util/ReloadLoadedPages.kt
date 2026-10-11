package com.photonne.app.ui.util

/** One page of a paged list, whatever its cursor is (an instant, an offset…). */
data class LoadedPage<T, C>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: C?,
)

/**
 * Re-reads a paged list from the start until it covers at least [loadedCount]
 * items (what the user had already scrolled through), or the list ends, and
 * returns everything as one page to swap in at once. Reloading only the first
 * page after a mutation or an Undo dropped the rest of the scroll and threw
 * the user back to the top. Duplicates across pages (the list moved while
 * reading it) are kept once, by [idOf]. A failure propagates so the caller
 * can keep what it had; [maxPages] bounds a runaway list.
 */
suspend fun <T, C> reloadLoadedPages(
    loadedCount: Int,
    idOf: (T) -> String,
    maxPages: Int = 25,
    fetch: suspend (cursor: C?) -> LoadedPage<T, C>,
): LoadedPage<T, C> {
    val items = ArrayList<T>()
    val seen = HashSet<String>()
    var cursor: C? = null
    var hasMore: Boolean
    var pages = 0
    do {
        val page = fetch(cursor)
        pages++
        page.items.forEach { if (seen.add(idOf(it))) items += it }
        hasMore = page.hasMore
        cursor = page.nextCursor
    } while (hasMore && cursor != null && items.size < loadedCount && pages < maxPages)
    return LoadedPage(items, hasMore, cursor)
}
