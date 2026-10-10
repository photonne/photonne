package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * What a RAW or HEIC/HEIF is downloaded (or shared) as. Every other format
 * always travels as it is, whatever is asked for.
 */
enum class DownloadFormat(val apiValue: String) {
    Original("original"),
    Jpeg("jpeg")
}

/**
 * Answer of `POST /api/assets/download-options`: whether a selection holds
 * anything there is a format to choose for.
 */
@Serializable
data class DownloadOptions(
    /** Assets of the selection the user can read. */
    val total: Int = 0,
    /** How many of them are RAW or HEIC/HEIF. */
    val convertibleCount: Int = 0,
    /** Their extensions, lower case and without the dot. */
    val extensions: List<String> = emptyList()
)
