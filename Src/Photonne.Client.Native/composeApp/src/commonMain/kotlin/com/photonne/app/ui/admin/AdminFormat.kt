package com.photonne.app.ui.admin

/** 1024-based human-readable bytes; mirrors `humanReadableBytes` in the
 *  account-storage screen so totals format the same way across the app. */
internal fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = ((value * 100).toLong()).toDouble() / 100.0
    return "$formatted ${units[unitIndex]}"
}

/** Strip the time portion from an ISO-8601 timestamp so dates render
 *  consistently regardless of whether the server includes fractional
 *  seconds or a timezone offset. Falls back to the raw value when the
 *  string doesn't look like ISO-8601. */
internal fun isoDateOnly(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val tIdx = value.indexOf('T')
    return if (tIdx > 0) value.substring(0, tIdx) else value
}
