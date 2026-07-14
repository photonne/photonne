package com.photonne.app.ui.timeline

import com.photonne.app.data.models.TimelineItem

data class MemoryGroup(
    val items: List<TimelineItem>,
    val yearsAgo: Int
) {
    val cover: TimelineItem get() = items.first()
    val count: Int get() = items.size
}

internal fun groupMemoriesByDay(
    items: List<TimelineItem>,
    currentYear: Int
): List<MemoryGroup> {
    if (items.isEmpty()) return emptyList()
    // The server already constrains a memory to a single calendar day (it
    // filters by CapturedAt month+day in the configured local timezone), so the
    // only meaningful axis here is the year. Capture dates are decoded via
    // captureLocalDate (the photo's own wall-clock), matching the server's frame.
    return items
        .groupBy { it.fileCreatedAt.captureLocalDate().year }
        .map { (year, group) ->
            val yearsAgo = (currentYear - year).coerceAtLeast(1)
            MemoryGroup(items = group, yearsAgo = yearsAgo)
        }
}
