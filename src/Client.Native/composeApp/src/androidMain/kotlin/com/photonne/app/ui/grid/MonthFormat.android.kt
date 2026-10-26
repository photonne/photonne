package com.photonne.app.ui.grid

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun formatLocalizedMonth(date: LocalDate): String {
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val raw = formatter.format(date.toJavaLocalDate())
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

actual fun formatLocalizedShortDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(date.toJavaLocalDate())

actual fun formatLocalizedMonthShort(date: LocalDate): String {
    val locale = Locale.getDefault()
    val raw = DateTimeFormatter.ofPattern("LLL", locale).format(date.toJavaLocalDate())
    return raw.trimEnd('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
