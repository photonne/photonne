package com.photonne.app.ui.folder

import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.folder.filterSharedFolders
import com.photonne.app.data.models.FolderSummary

/** Which slice of the folder list the user is looking at. */
enum class FoldersScope { All, Personal, Shared, External }

/**
 * The folder list split into the buckets [FoldersScope] filters by.
 *
 * `*Roots` are the top-level entries the list shows: direct children of the
 * user's home, direct children of the shared space, and one root per external
 * library. `*Descendants` are the same subtrees at full depth and only feed the
 * search field — searching for a folder should find it wherever it is nested,
 * but browsing shouldn't drown "Todas" in every sub-sub-folder.
 *
 * The three root buckets are disjoint by construction: [filterPersonalFolders]
 * and [filterSharedFolders] match on path alone, and an admin is free to point
 * an external library at a path inside `/assets/shared/` or a user's home, so
 * the library guard has to be applied here. Without it the same folder lands in
 * two buckets and the unified list feeds `LazyColumn` duplicate keys, which
 * throws rather than merely looking odd.
 */
data class FolderPartition(
    val personalRoots: List<FolderSummary> = emptyList(),
    val sharedRoots: List<FolderSummary> = emptyList(),
    val externalRoots: List<FolderSummary> = emptyList(),
    val personalDescendants: List<FolderSummary> = emptyList(),
    val sharedDescendants: List<FolderSummary> = emptyList(),
    val externalDescendants: List<FolderSummary> = emptyList()
)

private const val SHARED_PREFIX = "/assets/shared/"

private fun FolderSummary.normalizedPath(): String =
    path.replace('\\', '/').trimEnd('/')

private fun String.hasSystemSegment(): Boolean =
    split('/').any { it.equals("_trash", ignoreCase = true) || it.equals("_archive", ignoreCase = true) }

fun partitionFolders(all: List<FolderSummary>, username: String?): FolderPartition {
    val personalRoots = if (username.isNullOrBlank()) emptyList()
    else filterPersonalFolders(all, username)
        .filter { !it.isShared && it.externalLibraryId == null }

    val sharedRoots = filterSharedFolders(all)
        .filter { it.externalLibraryId == null }

    val personalDescendants = if (username.isNullOrBlank()) emptyList() else {
        val homePrefix = "/assets/users/$username/"
        all.filter { folder ->
            if (folder.isShared || folder.externalLibraryId != null) return@filter false
            val normalized = folder.normalizedPath()
            if (!normalized.startsWith(homePrefix, ignoreCase = true)) return@filter false
            !normalized.substring(homePrefix.length).hasSystemSegment()
        }
    }

    val sharedDescendants = all.filter { folder ->
        folder.externalLibraryId == null &&
            folder.normalizedPath().startsWith(SHARED_PREFIX, ignoreCase = true)
    }

    val externalDescendants = all.filter { it.externalLibraryId != null }

    return FolderPartition(
        personalRoots = personalRoots,
        sharedRoots = sharedRoots,
        externalRoots = resolveLibraryRoots(all),
        personalDescendants = personalDescendants,
        sharedDescendants = sharedDescendants,
        externalDescendants = externalDescendants
    )
}

/**
 * One entry per external library: the folder carrying that library id whose
 * parent doesn't belong to the same library. Mirrors the server's own root
 * lookup (`GET /api/folders/library/{id}/root`).
 */
internal fun resolveLibraryRoots(folders: List<FolderSummary>): List<FolderSummary> {
    val byId = folders.associateBy { it.id }
    return folders
        .filter { folder ->
            val libId = folder.externalLibraryId ?: return@filter false
            val parentId = folder.parentFolderId ?: return@filter true
            byId[parentId]?.externalLibraryId != libId
        }
        .distinctBy { it.externalLibraryId }
}
