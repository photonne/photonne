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
