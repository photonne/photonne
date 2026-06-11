package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AlbumShareLink(
    val token: String,
    val albumId: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val expiresAt: Instant? = null,
    val hasPassword: Boolean = false,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null,
    val viewCount: Int = 0,
    val shareUrl: String = ""
)

@Serializable
data class ShareUpdateResult(
    val token: String,
    @Serializable(with = FlexibleInstantSerializer::class) val expiresAt: Instant? = null,
    val hasPassword: Boolean = false,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null
)

/**
 * A public share link created by the current user, enriched with the target it points to
 * (album or asset). Returned by `GET /api/share/sent` to power the "My links" tab, which
 * lists links across all albums rather than a single album's links.
 */
@Serializable
data class SentShareLink(
    val token: String,
    @Serializable(with = FlexibleInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val expiresAt: Instant? = null,
    val hasPassword: Boolean = false,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null,
    val viewCount: Int = 0,
    val assetId: String? = null,
    val assetFileName: String? = null,
    val assetType: String? = null,
    val assetThumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val albumCoverUrl: String? = null,
    val shareUrl: String = ""
) {
    /** A display title: the album name, falling back to the shared file name. */
    val title: String? get() = albumName ?: assetFileName

    /** Relative thumbnail URL for the link's target, if any. */
    val thumbnailUrl: String? get() = albumCoverUrl ?: assetThumbnailUrl

    /** Adapts to [AlbumShareLink] so the shared edit dialog can be reused as-is. */
    fun toAlbumShareLink(): AlbumShareLink = AlbumShareLink(
        token = token,
        albumId = albumId,
        createdAt = createdAt,
        expiresAt = expiresAt,
        hasPassword = hasPassword,
        allowDownload = allowDownload,
        maxViews = maxViews,
        viewCount = viewCount,
        shareUrl = shareUrl
    )
}
