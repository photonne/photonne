package com.photonne.app.ui.grid

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Localized day header used by the Timeline grid at day-grouping zoom
 * levels. Mirrors [formatLocalizedMonth]: each platform uses its native
 * date formatter so the result follows the user's locale (e.g.
 * "Sat, 9 May 2026" in en-US, "sáb, 9 may 2026" in es-ES). With
 * [withYear] false the year is dropped ("Sat, 9 May").
 */
expect fun formatLocalizedDay(date: LocalDate, withYear: Boolean = true): String

/**
 * Header for a day group: "Hoy" / "Ayer" for those two days, the date
 * without the year inside the current year and the full date otherwise.
 * Capture dates are naive wall-clock (see captureLocalDate), so "today"
 * is the device's local date.
 *
 * The words follow the app's own language rule (composeResources ships
 * Spanish as default and English under values-en), so a non-English
 * device gets "Hoy" like the rest of the UI. This runs outside
 * composition (entry building), hence no stringResource.
 */
fun formatDayHeader(
    date: LocalDate,
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
): String {
    val english = Locale.current.language == "en"
    return when (date) {
        today -> if (english) "Today" else "Hoy"
        today.minus(DatePeriod(days = 1)) -> if (english) "Yesterday" else "Ayer"
        else -> formatLocalizedDay(date, withYear = date.year != today.year)
    }
}
