package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary
import com.photonne.app.ui.util.SortDirection
import com.photonne.app.ui.util.applyDirection
import com.photonne.app.ui.util.parseSortDirection
import com.photonne.app.ui.util.sortedByNatural
import com.russhwolf.settings.Settings

/** Persisted keys for the folder sort preference (shared across folder screens). */
internal const val FOLDERS_SORT_KEY = "photonne.folders.sort"
internal const val FOLDERS_DIRECTION_KEY = "photonne.folders.sortDirection"

/** Persisted key for the list/grid view mode (shared across folder screens). */
internal const val FOLDERS_VIEW_MODE_KEY = "photonne.folders.viewMode"

/** Read the persisted [FolderViewMode], defaulting to [FolderViewMode.List]. */
internal fun readFolderViewMode(settings: Settings): FolderViewMode =
    settings.getStringOrNull(FOLDERS_VIEW_MODE_KEY)
        ?.let { runCatching { FolderViewMode.valueOf(it) }.getOrNull() }
        ?: FolderViewMode.List

/** Natural default direction when a criterion is freshly picked. */
internal fun FolderSort.defaultDirection(): SortDirection = when (this) {
    FolderSort.Name -> SortDirection.Ascending // A→Z
    FolderSort.AssetCount -> SortDirection.Descending // most first
}

/** Read the persisted [FolderSort], defaulting to [FolderSort.Name]. */
internal fun readFolderSort(settings: Settings): FolderSort =
    settings.getStringOrNull(FOLDERS_SORT_KEY)
        ?.let { runCatching { FolderSort.valueOf(it) }.getOrNull() }
        ?: FolderSort.Name

/** Read the persisted [SortDirection], defaulting to the [sort]'s natural direction. */
internal fun readFolderDirection(settings: Settings, sort: FolderSort): SortDirection =
    parseSortDirection(settings.getStringOrNull(FOLDERS_DIRECTION_KEY), sort.defaultDirection())

/**
 * Order a folder list by the chosen [sort] and [direction]. Name sort is natural +
 * case-insensitive (see [sortedByNatural]); blank names fall back to the folder path.
 */
internal fun sortFolders(
    folders: List<FolderSummary>,
    sort: FolderSort,
    direction: SortDirection
): List<FolderSummary> {
    val ascending = when (sort) {
        FolderSort.Name -> folders.sortedByNatural { it.name.ifBlank { it.path } }
        FolderSort.AssetCount -> folders.sortedBy { it.assetCount }
    }
    return ascending.applyDirection(direction)
}
