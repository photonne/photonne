package com.photonne.app.data.folder

import com.photonne.app.data.models.FolderSummary

/**
 * Keeps only the direct children of the current user's home
 * (`/assets/users/{username}/{child}`), dropping the home itself, deeper
 * descendants (the Detail screen handles drill-down via `subFolders`), and
 * the `_trash` / `_archive` system folders that are surfaced from their own
 * menus.
 */
fun filterPersonalFolders(
    folders: List<FolderSummary>,
    username: String
): List<FolderSummary> {
    if (username.isBlank()) return emptyList()
    val homePrefix = "/assets/users/$username/"
    return folders.filter { folder ->
        val normalized = folder.path.replace('\\', '/').trimEnd('/')
        if (!normalized.startsWith(homePrefix, ignoreCase = true)) return@filter false
        val rest = normalized.substring(homePrefix.length)
        if (rest.isEmpty() || rest.contains('/')) return@filter false
        if (rest.equals("_trash", ignoreCase = true)) return@filter false
        if (rest.equals("_archive", ignoreCase = true)) return@filter false
        true
    }
}

/**
 * Direct children of `/assets/shared/`, excluding the shared root itself and
 * any deeper descendants.
 */
fun filterSharedFolders(folders: List<FolderSummary>): List<FolderSummary> {
    val prefix = "/assets/shared/"
    return folders.filter { folder ->
        val normalized = folder.path.replace('\\', '/').trimEnd('/')
        if (!normalized.startsWith(prefix, ignoreCase = true)) return@filter false
        val rest = normalized.substring(prefix.length)
        rest.isNotEmpty() && !rest.contains('/')
    }
}

/**
 * Shared folders the user may file assets into — the writable destinations for
 * the move picker. Read-only shares (browsable but not writable) are excluded
 * so the picker never offers a target that would 403 on move.
 */
fun writableSharedFolders(folders: List<FolderSummary>): List<FolderSummary> =
    filterSharedFolders(folders).filter { it.canWrite || it.isOwner }

/**
 * Every folder the user may move assets into, at any depth — the interior of the
 * user's home and of the shared space. Feeds the move picker, which renders it as
 * a collapsible tree. The home (`/assets/users/{username}`) and shared
 * (`/assets/shared`) roots are namespace containers, never a destination you file
 * into, so they are dropped and their children surface as the tree's top level.
 * Excludes external libraries (read-only) and the `_trash` / `_archive` system
 * folders. The picker itself drops the source folder and its own descendants via
 * `excludeFolderId`.
 */
fun writableMoveDestinations(folders: List<FolderSummary>, username: String?): List<FolderSummary> {
    val homePrefix = if (username.isNullOrBlank()) null else "/assets/users/$username/"
    val sharedPrefix = "/assets/shared/"
    return folders.filter { folder ->
        if (!folder.canWrite || folder.externalLibraryId != null) return@filter false
        val normalized = folder.path.replace('\\', '/').trimEnd('/')
        val inside = (homePrefix != null && normalized.startsWith(homePrefix, ignoreCase = true)) ||
            normalized.startsWith(sharedPrefix, ignoreCase = true)
        if (!inside) return@filter false
        normalized.split('/').none { segment ->
            segment.equals("_trash", ignoreCase = true) || segment.equals("_archive", ignoreCase = true)
        }
    }
}
