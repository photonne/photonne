package com.photonne.app.ui.grid

import kotlinx.datetime.LocalDate

/**
 * Localized day header used by the Timeline grid at day-grouping zoom
 * levels. Mirrors [formatLocalizedMonth]: each platform uses its native
 * date formatter so the result follows the user's locale (e.g.
 * "Sat, 9 May 2026" in en-US, "sáb, 9 may 2026" in es-ES).
 */
expect fun formatLocalizedDay(date: LocalDate): String
