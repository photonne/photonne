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
    val sharedWithCount: Int = 0,
    val externalLibraryId: String? = null,
    // Per-user opt-out: I only administer this shared folder; keep it out of my
    // timeline, memories, people and search (still browsable here).
    val excludedFromTimeline: Boolean = false,
    val subFolders: List<FolderSummary> = emptyList()
)
