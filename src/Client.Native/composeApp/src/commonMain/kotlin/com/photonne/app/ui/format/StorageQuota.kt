package com.photonne.app.ui.format

/**
 * Share of the quota from which usage reads as a warning: the point where it is
 * worth freeing space before uploads start being refused. The web uses the same
 * 90 %, so both clients turn amber at the same moment.
 */
const val STORAGE_QUOTA_WARNING_FRACTION = 0.9f

/** How full a quota is, as every screen that shows one colours it. */
enum class StorageQuotaLevel { Normal, Warning, Exceeded }

/**
 * Used share of [quotaBytes], NOT capped at 1: 1.25 is a quota overrun by a
 * quarter. Null when there is no quota. The screens used to clamp it to 100 %,
 * which made "full" and "well past the limit" look the same.
 */
fun storageQuotaFraction(usedBytes: Long, quotaBytes: Long?): Float? =
    quotaBytes?.takeIf { it > 0 }?.let { quota -> (usedBytes.toDouble() / quota.toDouble()).toFloat() }

/** Exceeded only once usage is past the quota; exactly full is still a warning. */
fun storageQuotaLevel(fraction: Float): StorageQuotaLevel = when {
    fraction > 1f -> StorageQuotaLevel.Exceeded
    fraction >= STORAGE_QUOTA_WARNING_FRACTION -> StorageQuotaLevel.Warning
    else -> StorageQuotaLevel.Normal
}
