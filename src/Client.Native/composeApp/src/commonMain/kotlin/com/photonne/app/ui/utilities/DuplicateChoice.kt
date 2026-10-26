package com.photonne.app.ui.utilities

import com.photonne.app.data.models.TimelineItem

/*
 * Choosing which copies of an exact duplicate go to the trash, as the web does
 * (lib/search/utilities/duplicates.ts). A group always keeps one copy, so the
 * review can never delete the photo itself.
 */

private fun TimelineItem.folderDepth(): Int = fullPath.count { it == '/' }

/**
 * The copy worth keeping. The copies are byte for byte the same, so size says
 * nothing: a favourite wins, then the one closest to the root (copies tend to
 * live in "Backup/…" or "copia/…" subfolders), then the oldest, then by id so
 * the choice is stable.
 */
internal fun bestCopy(assets: List<TimelineItem>): TimelineItem? =
    assets.minWithOrNull(
        compareByDescending<TimelineItem> { it.isFavorite }
            .thenBy { it.folderDepth() }
            .thenBy { it.fileCreatedAt }
            .thenBy { it.id }
    )

/** Keeps only [keeperId] of [assets]: every other copy is marked. */
internal fun keepOnlyCopy(assets: List<TimelineItem>, keeperId: String): Set<String> =
    assets.mapNotNullTo(LinkedHashSet()) { it.id.takeIf { id -> id != keeperId } }

/**
 * Marks or unmarks one copy. Marking the last copy left would empty the group,
 * so it is refused: the set comes back unchanged.
 */
internal fun toggleCopy(assets: List<TimelineItem>, marked: Set<String>, id: String): Set<String> {
    if (id in marked) return marked - id
    val kept = assets.count { it.id !in marked }
    return if (kept <= 1) marked else marked + id
}

/** One folder split into what every copy shares and what sets it apart. */
internal data class FolderSplit(val common: String, val own: String)

/**
 * Splits each folder by whole path segments into the part every copy shares
 * and the part that tells them apart: `/fotos/2024/` + `Camera` vs `Backup`.
 * When all copies are in the same folder, nothing sets them apart.
 */
internal fun splitFolders(folders: List<String>): List<FolderSplit> {
    val parts = folders.map { it.split('/') }
    var shared = 0
    if (folders.toSet().size > 1) {
        val shortest = parts.minOf { it.size }
        while (shared < shortest - 1 && parts.all { it[shared] == parts[0][shared] }) shared++
    }
    return parts.mapIndexed { i, segments ->
        if (shared == 0) FolderSplit(folders[i], "")
        else FolderSplit(
            segments.take(shared).joinToString("/") + "/",
            segments.drop(shared).joinToString("/")
        )
    }
}

/** The folder of a file path ("/a/b/c.jpg" → "/a/b"). */
internal fun folderOf(path: String): String = path.substringBeforeLast('/', "")
