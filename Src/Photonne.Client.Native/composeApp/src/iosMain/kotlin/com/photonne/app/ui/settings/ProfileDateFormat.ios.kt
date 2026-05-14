package com.photonne.app.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@OptIn(ExperimentalForeignApi::class)
actual fun formatProfileDate(instant: Instant): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = NSDateFormatter.dateFormatFromTemplate(
            "dd MMM yyyy",
            options = 0u,
            locale = NSLocale.currentLocale
        ) ?: "dd MMM yyyy"
    }
    return formatter.stringFromDate(instant.toNSDate())
}

@OptIn(ExperimentalForeignApi::class)
actual fun formatProfileDateTime(instant: Instant): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = NSDateFormatter.dateFormatFromTemplate(
            "dd MMM yyyy HH:mm",
            options = 0u,
            locale = NSLocale.currentLocale
        ) ?: "dd MMM yyyy HH:mm"
    }
    return formatter.stringFromDate(instant.toNSDate())
}
