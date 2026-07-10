package com.photonne.app.ui.timeline

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Capture timestamps (fileCreatedAt / dateTaken / capturedAt) are stored on the
 * server as the photo's OWN local wall-clock — the time the camera recorded,
 * with no zone — and travel over the wire labelled UTC. They must therefore be
 * decoded in [TimeZone.UTC] to recover that wall-clock verbatim.
 *
 * Using the viewer's device zone ([TimeZone.currentSystemDefault]) here would
 * re-apply an offset and shift the day around midnight (and make "on this day"
 * and the timeline day/month headers disagree with the server, which groups on
 * the stored value directly). "Now" is still read in the device zone — only the
 * stored capture instants use this.
 */
fun Instant.captureLocalDate(): LocalDate = toLocalDateTime(TimeZone.UTC).date

fun Instant.captureLocalDateTime(): LocalDateTime = toLocalDateTime(TimeZone.UTC)
