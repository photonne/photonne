package com.photonne.app.ui.util

/**
 * Direction applied on top of a sort criterion. Each criterion computes a base
 * ascending order (A→Z, oldest→newest, fewest→most); [Descending] reverses it.
 */
enum class SortDirection { Ascending, Descending }

/** Parse a persisted [SortDirection] name, falling back to [default]. */
fun parseSortDirection(raw: String?, default: SortDirection): SortDirection =
    raw?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() } ?: default

/** Apply [direction] to a list already sorted ascending by the chosen criterion. */
fun <T> List<T>.applyDirection(direction: SortDirection): List<T> =
    if (direction == SortDirection.Descending) this.asReversed().toList() else this
