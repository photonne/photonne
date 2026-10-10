package com.photonne.app.data.models

import kotlin.time.Instant
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
    val allowUpload: Boolean = false,
    val uploadCount: Int = 0,
    val shareUrl: String = ""
)

@Serializable
data class ShareUpdateResult(
    val token: String,
    @Serializable(with = FlexibleInstantSerializer::class) val expiresAt: Instant? = null,
    val hasPassword: Boolean = false,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null,
    val allowUpload: Boolean = false
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
    val allowUpload: Boolean = false,
    val uploadCount: Int = 0,
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
        allowUpload = allowUpload,
        uploadCount = uploadCount,
        shareUrl = shareUrl
    )
}

/**
 * Vista pública de un enlace (`GET /api/share/{token}`, sin sesión): lo mismo
 * que pinta la página web `/share/{token}`. Las URL de miniatura y contenido
 * vienen relativas al servidor que emitió el enlace (con `?pw=` si lo lleva).
 */
@Serializable
data class PublicShareContent(
    val token: String = "",
    val requiresPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val allowDownload: Boolean = true,
    val allowUpload: Boolean = false,
    val album: PublicSharedAlbum? = null,
    val assets: List<PublicSharedAsset>? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val expiresAt: Instant? = null
)

@Serializable
data class PublicSharedAlbum(
    val name: String = "",
    val description: String? = null,
    val assetCount: Int = 0,
    val coverThumbnailUrl: String? = null
)

@Serializable
data class PublicSharedAsset(
    val id: String,
    val fileName: String = "",
    /** "Image" / "Video" (nombre del enum del servidor). */
    val type: String = "",
    val fileSize: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailUrl: String = "",
    val contentUrl: String = ""
) {
    val isVideo: Boolean get() = type.equals("Video", ignoreCase = true)
}
