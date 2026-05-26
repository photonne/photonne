package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineItem
import kotlinx.datetime.Instant

class TimelineRepository(
    private val api: PhotonneApi,
    private val pageSize: Int = PhotonneApi.DEFAULT_TIMELINE_PAGE_SIZE
) {
    /**
     * Fetches a single page. Pass `cursor = null` for the first page; for
     * subsequent pages, forward the `nextCursor` returned by the previous
     * call. The result is null-cursor-safe — callers can stop paging when
     * `result.hasMore` is false or `result.nextCursor` is null.
     */
    suspend fun loadPage(cursor: Instant?): TimelinePageResult {
        val page = api.getTimeline(cursor = cursor, pageSize = pageSize)
        return TimelinePageResult(
            items = page.items,
            nextCursor = page.nextCursor,
            hasMore = page.hasMore && page.nextCursor != null
        )
    }

    /**
     * Slim "give me the N most recent assets" call used by the hub's recents
     * row. Hits a dedicated endpoint that skips the timeline's heavy
     * filesystem scan + tag joins, so the hub stays responsive even when the
     * full timeline query is slow.
     */
    suspend fun loadRecent(limit: Int = 10): List<TimelineItem> = api.getRecentAssets(limit)
}

data class TimelinePageResult(
    val items: List<TimelineItem>,
    val nextCursor: Instant?,
    val hasMore: Boolean
)
