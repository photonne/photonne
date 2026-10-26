package com.photonne.app.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.grid.formatLocalizedShortDate
import com.photonne.app.ui.timeline.captureLocalDate

/**
 * What a selection spans, under its count: "12 mar 2024 – 3 abr 2024 · 1,2 GB"
 * (one date when it all falls on the same day). Counts the selected items it
 * can see in [items]; null when none of them is there. Device-only files have
 * no size worth adding up against the server's, so they don't count.
 */
fun selectionSummary(items: List<TimelineItem>, selection: Set<String>): String? {
    if (selection.isEmpty()) return null
    val selected = items.filter { it.id in selection && !it.isLocalOnly }
    if (selected.isEmpty()) return null
    val first = selected.minOf { it.fileCreatedAt }.captureLocalDate()
    val last = selected.maxOf { it.fileCreatedAt }.captureLocalDate()
    val range = if (first == last) formatLocalizedShortDate(first)
    else "${formatLocalizedShortDate(first)} – ${formatLocalizedShortDate(last)}"
    return "$range · ${humanBytes(selected.sumOf { it.fileSize })}"
}

@Composable
fun rememberSelectionSummary(items: List<TimelineItem>, selection: Set<String>): String? =
    remember(items, selection) { selectionSummary(items, selection) }
