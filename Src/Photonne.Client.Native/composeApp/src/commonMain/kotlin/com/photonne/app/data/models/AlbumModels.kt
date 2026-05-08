package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AlbumSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val updatedAt: Instant,
    val assetCount: Int = 0,
    val coverThumbnailUrl: String? = null,
    val previewThumbnailUrls: List<String> = emptyList(),
    val isOwner: Boolean = true,
    val isShared: Boolean = false,
    val sharedWithCount: Int = 0,
    val canRead: Boolean = true,
    val canWrite: Boolean = false,
    val canDelete: Boolean = false,
    val canManagePermissions: Boolean = false,
    val hasActiveShareLink: Boolean = false
)
