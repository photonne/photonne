package com.photonne.app.data.folder

import com.photonne.app.data.models.FolderSummary

/**
 * Keeps only folders inside the current user's home (`/assets/users/{username}`),
 * dropping the home itself plus any `_trash` / `_archive` system folders that
 * are surfaced from their dedicated menus.
 */
fun filterPersonalFolders(
    folders: List<FolderSummary>,
    username: String
): List<FolderSummary> {
    if (username.isBlank()) return emptyList()
    val homePrefix = "/assets/users/$username/"
    return folders.filter { folder ->
        val normalized = folder.path.replace('\\', '/')
        if (!normalized.startsWith(homePrefix, ignoreCase = true)) return@filter false
        if (normalized.contains("/_trash", ignoreCase = true)) return@filter false
        if (normalized.contains("/_archive", ignoreCase = true)) return@filter false
        true
    }
}
