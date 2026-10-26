package com.photonne.app.ui.timeline

import com.photonne.app.data.timeline.TimelineBucketState
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One year of the jump sheet: its total and the twelve months' counts (January first). */
data class JumpYear(val year: Int, val total: Int, val months: List<Int>)

/**
 * The timeline's skeleton ("yyyy-MM" buckets with their counts) as years,
 * newest first, each with all twelve months so the sheet can show the empty
 * ones too. Years without photos aren't listed.
 */
fun jumpYears(buckets: List<TimelineBucketState>): List<JumpYear> {
    val counts = HashMap<Int, IntArray>()
    for (bucket in buckets) {
        val year = bucket.key.take(4).toIntOrNull() ?: continue
        val month = bucket.key.drop(5).take(2).toIntOrNull()?.takeIf { it in 1..12 } ?: continue
        counts.getOrPut(year) { IntArray(12) }[month - 1] += bucket.count
    }
    return counts.entries
        .map { (year, months) -> JumpYear(year, months.sum(), months.toList()) }
        .filter { it.total > 0 }
        .sortedByDescending { it.year }
}

/**
 * Where a jump to [month] of [year] lands: its last day. The timeline goes
 * newest first, so that is the month's first row whether it groups by month
 * or by day (findRowIndexForDate takes the first header at or before it).
 */
fun jumpTarget(year: Int, month: Int): Instant =
    LocalDate(year, month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .atStartOfDayIn(TimeZone.UTC)
