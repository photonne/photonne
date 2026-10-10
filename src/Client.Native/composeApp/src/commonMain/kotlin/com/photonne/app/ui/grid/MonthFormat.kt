package com.photonne.app.ui.grid

import kotlinx.datetime.LocalDate

/**
 * Localized "Month Year" header used in the grouped timeline. Each
 * platform uses its native date formatting library so the result
 * follows the user's locale (e.g. "May 2026" in en-US,
 * "mai 2026" in fr-FR, "Mayo 2026" in es-ES).
 */
expect fun formatLocalizedMonth(date: LocalDate): String
