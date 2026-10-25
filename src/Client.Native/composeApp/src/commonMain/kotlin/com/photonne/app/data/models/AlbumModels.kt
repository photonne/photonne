package com.photonne.app.data.models

import kotlin.time.Instant
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
    val hasActiveShareLink: Boolean = false,
    // Fijado por MÍ (personal: no afecta a los demás miembros). Servidores
    // antiguos no lo envían → false.
    val isPinned: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val pinnedAt: Instant? = null,
    // Mi orden en "Fijados" (uno para álbumes y carpetas, el más bajo arriba).
    // Servidores antiguos no lo envían → null, y se ordena por pinnedAt.
    val pinPosition: Int? = null,
    // "Manual" or "Smart" (docs/smart-albums/). Defaults keep decode safe
    // against older servers that don't emit the field.
    val kind: String = "Manual"
) {
    val isSmart: Boolean get() = kind.equals("Smart", ignoreCase = true)
}

/**
 * A photo taken out of an album in the last 30 days, with who did it
 * (GET /api/albums/{id}/assets/removed). [removedByUsername] is null once
 * that account is gone.
 */
@Serializable
data class RemovedAlbumAsset(
    val asset: TimelineItem,
    @Serializable(with = FlexibleInstantSerializer::class) val removedAt: Instant,
    val removedByUserId: String? = null,
    val removedByUsername: String? = null
)

/** Answer of POST /api/albums/{id}/assets/remove: the ids taken out, in album order. */
@Serializable
data class RemoveAlbumAssetsResult(
    val removed: List<String> = emptyList(),
    val skipped: Int = 0
)

/** Answer of POST /api/albums/{id}/assets/restore: the ids back in the album. */
@Serializable
data class RestoreAlbumAssetsResult(
    val restored: List<String> = emptyList(),
    val skipped: Int = 0
)
