package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * One group of duplicate assets surfaced by `GET /api/utilities/duplicates`.
 *
 * The server groups by SHA-256 checksum and only returns groups with two
 * or more live assets, so [assets] is always ≥ 2. [totalSize] is the sum
 * of the entire group (so "wasted" bytes for the group is
 * `totalSize - assets[0].fileSize` after the user picks one to keep).
 */
@Serializable
data class UserDuplicateGroup(
    val hash: String,
    val totalSize: Long,
    val assets: List<TimelineItem>
)

/**
 * Tree node returned by `GET /api/utilities/folders/tree`. The same DTO
 * shape exists in [FolderSummary] but with a flatter list of folders;
 * here the server recurses, embedding children under [subFolders] so
 * the client can render the hierarchy without follow-up requests.
 */
@Serializable
data class FolderTreeNode(
    val id: String,
    val path: String,
    val name: String,
    val parentFolderId: String? = null,
    val assetCount: Int = 0,
    val firstAssetId: String? = null,
    val previewAssetIds: List<String> = emptyList(),
    val isShared: Boolean = false,
    val isOwner: Boolean = true,
    val sharedWithCount: Int = 0,
    val externalLibraryId: String? = null,
    val subFolders: List<FolderTreeNode> = emptyList()
)
