package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FolderSummary(
    val id: String,
    val path: String,
    val name: String,
    val parentFolderId: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val createdAt: Instant,
    val assetCount: Int = 0,
    val firstAssetId: String? = null,
    val previewAssetIds: List<String> = emptyList(),
    val isShared: Boolean = false,
    val isOwner: Boolean = true,
    // True when the current user may move/upload assets into this folder. Set by
    // the folder-list/tree endpoints; used to offer only writable folders as
    // move destinations.
    val canWrite: Boolean = true,
    val sharedWithCount: Int = 0,
    val externalLibraryId: String? = null,
    // Per-user opt-out: I only administer this shared folder; keep it out of my
    // timeline, memories, people and search (still browsable here).
    val excludedFromDiscovery: Boolean = false,
    val subFolders: List<FolderSummary> = emptyList()
)
