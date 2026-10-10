package com.photonne.app.ui.grid

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@OptIn(ExperimentalForeignApi::class)
actual fun formatLocalizedDay(date: LocalDate, withYear: Boolean): String {
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.monthNumber.toLong()
        day = date.dayOfMonth.toLong()
    }
    val nsDate = NSCalendar.currentCalendar.dateFromComponents(components)
        ?: return "${date.year}-${date.monthNumber}-${date.dayOfMonth}"
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = NSDateFormatter.dateFormatFromTemplate(
            if (withYear) "EEEdMMMyyyy" else "EEEdMMM",
            options = 0u,
            locale = NSLocale.currentLocale
        ) ?: if (withYear) "EEE, d MMM yyyy" else "EEE, d MMM"
    }
    return formatter.stringFromDate(nsDate)
}
