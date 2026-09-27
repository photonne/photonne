package com.photonne.app.ui.grid

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun formatLocalizedDay(date: LocalDate, withYear: Boolean): String {
    val locale = Locale.getDefault()
    val pattern = if (withYear) "EEE, d MMM yyyy" else "EEE, d MMM"
    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
    val raw = formatter.format(date.toJavaLocalDate())
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
