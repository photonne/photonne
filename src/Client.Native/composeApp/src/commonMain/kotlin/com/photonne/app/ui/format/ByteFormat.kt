package com.photonne.app.ui.format

import kotlin.math.roundToLong

/**
 * 1024-based human-readable byte size, the only one in the app. Lives in a
 * neutral package because admin, utilities, storage, upload and backup all
 * report sizes and they should read the same: before, four copies printed
 * the same file as "2.0 GB", "2 GB" or "2.37 GB".
 *
 * One decimal below 100 and none from there ("2,4 GB", "512 MB"), rounded
 * rather than truncated, with a decimal comma to match the "." thousands
 * grouping of `formatCount` (the interface stays in Spanish).
 */
internal fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val tenths = (value * 10).roundToLong()
    val formatted = when {
        value >= 100 -> value.roundToLong().toString()
        tenths % 10 == 0L -> (tenths / 10).toString()
        else -> "${tenths / 10},${tenths % 10}"
    }
    return "$formatted ${units[unitIndex]}"
}
