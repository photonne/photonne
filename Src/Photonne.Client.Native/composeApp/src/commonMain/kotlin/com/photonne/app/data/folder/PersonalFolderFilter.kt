package com.photonne.app.data.folder

import com.photonne.app.data.models.FolderSummary

/**
 * Keeps only folders inside the current user's home (`/assets/users/{userId}`),
 * dropping the home itself plus any `_trash` / `_archive` system folders that
 * are surfaced from their dedicated menus.
 */
fun filterPersonalFolders(
    folders: List<FolderSummary>,
    userId: String
): List<FolderSummary> {
    if (userId.isBlank()) return emptyList()
    val homePrefix = "/assets/users/$userId/"
    return folders.filter { folder ->
        val normalized = folder.path.replace('\\', '/')
        if (!normalized.startsWith(homePrefix, ignoreCase = true)) return@filter false
        if (normalized.contains("/_trash", ignoreCase = true)) return@filter false
        if (normalized.contains("/_archive", ignoreCase = true)) return@filter false
        true
    }
}
