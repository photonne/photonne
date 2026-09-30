package com.photonne.app.data.models

import kotlin.time.Instant
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
    // User-editable tags (removable). Subset of [tags].
    val userTags: List<String> = emptyList(),
    // ML/auto-derived tags (LivePhoto, Portrait, …) — read-only. Subset of [tags].
    val autoTags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isFileMissing: Boolean = false,
    val caption: String? = null,
    val isReadOnly: Boolean = false,
    /** The caller owns the asset. Per-asset AI analysis is owner-only on the
     *  server; defaults to true so an older server that doesn't say still
     *  offers the action (and answers 403 with a reason if it isn't). */
    val isOwner: Boolean = true,
    /** The caller may edit description, capture date and tags (the server's
     *  own rule for those endpoints). Defaults to true for older servers that
     *  don't send it; they still answer 403 and the viewer reverts. */
    val canEdit: Boolean = true,
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
    val software: String? = null,
    /** Ciudad más cercana (GeoNames), resuelta por el servidor al indexar. Null
     *  sin GPS, antes del backfill o con servidores que aún no la mandan. */
    val placeName: String? = null,
    /** ISO 3166-1 alfa-2 de [placeName]; el nombre del país lo pone el cliente. */
    val placeCountryCode: String? = null,
) {
    val cameraDisplay: String?
        get() = listOfNotNull(cameraMake, cameraModel)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
}

@Serializable
data class CaptureDateUpdateResponse(
    @Serializable(with = FlexibleInstantSerializer::class) val dateTaken: Instant? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val capturedAt: Instant? = null,
    val fileWritten: Boolean = false,
    val reason: String? = null
)

/**
 * Server preview of the date recoverable for one asset: from the physical
 * file's EXIF and/or inferred from the file name / folder path. Nothing is
 * applied — the user picks a candidate in the edit-date sheet.
 */
@Serializable
data class CaptureDateSuggestion(
    @Serializable(with = FlexibleInstantSerializer::class) val currentDate: Instant,
    val currentSource: String = "",
    @Serializable(with = FlexibleInstantSerializer::class) val exifDate: Instant? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val inferredDate: Instant? = null,
    val inferredOrigin: String? = null,
    /** Effective filesystem date (older of birthtime/mtime) — for EXIF-less
     *  files like PNGs, the mtime is often the only surviving real date. */
    @Serializable(with = FlexibleInstantSerializer::class) val fileDate: Instant? = null
)

@Serializable
data class ThumbnailInfo(
    val id: String,
    val size: String,
    val width: Int,
    val height: Int,
    val assetId: String
)

/** Una línea de texto reconocido (OCR) — `GET /api/assets/{id}/text`. */
@Serializable
data class RecognizedTextLine(
    val text: String = "",
    val confidence: Float = 0f,
    val lineIndex: Int = 0,
)

/** Un objeto detectado — `GET /api/assets/{id}/objects` (uno por caja, así
 *  que la misma etiqueta puede repetirse). */
@Serializable
data class DetectedObject(
    val label: String = "",
    val confidence: Float = 0f,
)

/** Una escena clasificada — `GET /api/assets/{id}/scenes`, por rango. */
@Serializable
data class ClassifiedScene(
    val label: String = "",
    val confidence: Float = 0f,
    val rank: Int = 0,
)
