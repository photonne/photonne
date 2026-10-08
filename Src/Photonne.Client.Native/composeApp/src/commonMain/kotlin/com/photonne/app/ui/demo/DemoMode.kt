package com.photonne.app.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the signed-in server is the public demo (`GET /api/admin/demo-info`).
 * Provided by the authenticated root; false until the server answers, and on any
 * server too old to know the endpoint.
 */
val LocalDemoMode = staticCompositionLocalOf { false }

/** Fixed length on purpose: the mask doesn't even give away how long the value is. */
private const val DEMO_REDACTED = "••••••••••••"

/**
 * [value] as the demo may show it. Keys, server URLs and disk paths are real
 * data of whoever runs the demo, so visitors see a mask instead; outside the
 * demo it is [value] unchanged.
 */
@Composable
fun demoRedacted(value: String): String =
    if (LocalDemoMode.current && value.isNotEmpty()) DEMO_REDACTED else value
