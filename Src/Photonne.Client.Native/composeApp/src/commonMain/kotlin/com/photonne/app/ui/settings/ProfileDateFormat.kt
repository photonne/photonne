package com.photonne.app.ui.settings

import kotlinx.datetime.Instant

/**
 * Localized rendering for the profile summary timestamps, mirroring the
 * PWA's "dd MMM yyyy" and "dd MMM yyyy HH:mm" formats while still
 * respecting the user's system locale.
 */
expect fun formatProfileDate(instant: Instant): String

expect fun formatProfileDateTime(instant: Instant): String
