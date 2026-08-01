package com.photonne.app.ui.admin

// Byte formatting lives in ui/format/ByteFormat.kt — admin, utilities and
// backup all report sizes and should format them identically.

/** Strip the time portion from an ISO-8601 timestamp so dates render
 *  consistently regardless of whether the server includes fractional
 *  seconds or a timezone offset. Falls back to the raw value when the
 *  string doesn't look like ISO-8601. */
internal fun isoDateOnly(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val tIdx = value.indexOf('T')
    return if (tIdx > 0) value.substring(0, tIdx) else value
}
