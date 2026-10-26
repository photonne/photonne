package com.photonne.app.data.actions

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Whole days until the nightly cleanup purges an item trashed at [deletedAt]
 * (it goes once it is older than the retention), rounded up; 0 means the next
 * cleanup takes it. Null without a retention or a deletion date.
 */
fun trashDaysLeft(deletedAt: Instant?, retentionDays: Int?, now: Instant): Int? {
    if (deletedAt == null || retentionDays == null || retentionDays <= 0) return null
    val left = deletedAt + retentionDays.days - now
    if (!left.isPositive()) return 0
    val wholeDays = left.inWholeDays.toInt()
    return if (left > wholeDays.days) wholeDays + 1 else wholeDays
}
