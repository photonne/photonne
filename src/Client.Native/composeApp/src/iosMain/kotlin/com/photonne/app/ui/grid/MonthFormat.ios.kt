package com.photonne.app.ui.grid

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@OptIn(ExperimentalForeignApi::class)
actual fun formatLocalizedMonth(date: LocalDate): String {
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.monthNumber.toLong()
        day = 1
    }
    val nsDate = NSCalendar.currentCalendar.dateFromComponents(components) ?: return "${date.year}-${date.monthNumber}"
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = NSDateFormatter.dateFormatFromTemplate(
            "LLLL yyyy",
            options = 0u,
            locale = NSLocale.currentLocale
        ) ?: "LLLL yyyy"
    }
    val raw = formatter.stringFromDate(nsDate)
    return raw.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
}
