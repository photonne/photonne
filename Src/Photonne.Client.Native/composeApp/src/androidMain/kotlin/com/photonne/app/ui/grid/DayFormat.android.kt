package com.photonne.app.ui.grid

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun formatLocalizedDay(date: LocalDate): String {
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    val raw = formatter.format(date.toJavaLocalDate())
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
