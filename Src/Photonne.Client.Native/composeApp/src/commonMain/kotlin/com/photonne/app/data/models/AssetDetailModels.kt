package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AssetDetail(
    val id: String,
    val fileName: String,
    val fullPath: String,
    val fileSize: Long,
    @Serializable(with = FlexibleInstantSerializer::class) val fileCreatedAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val fileModifiedAt: Instant,
    val extension: String,
    @Serializable(with = FlexibleInstantSerializer::class) val scannedAt: Instant,
    val type: String,
    val checksum: String? = null,
    val hasExif: Boolean = false,
    val hasThumbnails: Boolean = false,
    val folderId: String? = null,
    val folderPath: String? = null,
    val exif: ExifData? = null,
    val thumbnails: List<ThumbnailInfo> = emptyList(),
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isFileMissing: Boolean = false,
    val caption: String? = null,
    val aiDescription: String? = null,
    val isReadOnly: Boolean = false
) {
    val isVideo: Boolean get() = type.equals("VIDEO", ignoreCase = true)
}

@Serializable
data class ExifData(
    @Serializable(with = FlexibleInstantSerializer::class) val dateTaken: Instant? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val orientation: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val iso: Int? = null,
    val aperture: Double? = null,
    val shutterSpeed: Double? = null,
    val focalLength: Double? = null,
    val description: String? = null,
    val keywords: String? = null,
    val software: String? = null
) {
    val cameraDisplay: String?
        get() = listOfNotNull(cameraMake, cameraModel)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
}

@Serializable
data class ThumbnailInfo(
    val id: String,
    val size: String,
    val width: Int,
    val height: Int,
    val assetId: String
)
