package com.photonne.app.ui.util

import com.photonne.app.data.models.TimelineItem

/**
 * Undoes an optimistic removal after the server rejected it: puts the items of
 * [restoredIds] back from [previous] at their original positions, keeping
 * everything that changed in this list meanwhile (favourite toggles, a quiet
 * refresh, removals from other screens). Restoring the whole [previous]
 * snapshot instead would silently throw those changes away.
 */
fun List<TimelineItem>.withRestored(
    previous: List<TimelineItem>,
    restoredIds: Set<String>,
): List<TimelineItem> {
    val present = mapTo(HashSet()) { it.id }
    val missing = previous.filter { it.id in restoredIds && it.id !in present }
    if (missing.isEmpty()) return this
    val order = HashMap<String, Int>(previous.size)
    previous.forEachIndexed { index, item -> order[item.id] = index }

    val result = ArrayList<TimelineItem>(size + missing.size)
    var next = 0
    // Items that weren't in [previous] inherit the position of the item
    // before them, so they stay where they are relative to their neighbours.
    var position = -1
    for (item in this) {
        position = order[item.id] ?: position
        while (next < missing.size && order.getValue(missing[next].id) < position) {
            result += missing[next++]
        }
        result += item
    }
    while (next < missing.size) result += missing[next++]
    return result
}
