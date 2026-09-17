package com.photonne.app.ui.admin

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Byte formatting lives in ui/format/ByteFormat.kt — admin, utilities and
// backup all report sizes and should format them identically.

/**
 * "17/09/2026" in the phone's timezone, from the server's ISO-8601 UTC
 * timestamp. This used to cut the string at the 'T': a login at 23:30 UTC
 * showed the day before the one the admin lived it on, in yyyy-MM-dd. Falls
 * back to the date part of the raw value when it isn't a parseable instant.
 */
internal fun adminDate(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val local = parseInstant(value)?.toLocalDateTime(TimeZone.currentSystemDefault())
        ?: return value.substringBefore('T')
    return two(local.dayOfMonth) + "/" + two(local.monthNumber) + "/" + local.year
}

/** [adminDate] plus the local time: "17/09/2026 08:41". */
internal fun adminDateTime(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val local = parseInstant(value)?.toLocalDateTime(TimeZone.currentSystemDefault())
        ?: return value.take(16).replace('T', ' ')
    return adminDate(value) + " " + two(local.hour) + ":" + two(local.minute)
}

internal fun adminDate(instant: Instant): String = adminDate(instant.toString()).orEmpty()

// The server sends UTC with or without the trailing Z depending on the column.
private fun parseInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { Instant.parse(value + "Z") }.getOrNull()

private fun two(n: Int): String = n.toString().padStart(2, '0')

/** [formatCount] for the totals that come as Long. */
internal fun formatCount(value: Long): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "-$grouped" else grouped
}
