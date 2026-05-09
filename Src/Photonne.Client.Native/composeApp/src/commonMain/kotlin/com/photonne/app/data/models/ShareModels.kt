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
