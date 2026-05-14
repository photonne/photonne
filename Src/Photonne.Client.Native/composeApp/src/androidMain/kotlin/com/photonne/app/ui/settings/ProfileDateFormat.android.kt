package com.photonne.app.ui.settings

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun formatProfileDate(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        .format(ldt.toJavaLocalDateTime())
}

actual fun formatProfileDateTime(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.getDefault())
        .format(ldt.toJavaLocalDateTime())
}
