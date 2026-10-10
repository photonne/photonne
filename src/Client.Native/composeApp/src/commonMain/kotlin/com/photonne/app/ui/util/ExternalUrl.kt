package com.photonne.app.ui.util

/**
 * Opens an external URL in the user's default browser / handler.
 * On Android: Intent.ACTION_VIEW with the parsed Uri.
 * On iOS: UIApplication.sharedApplication.openURL(_:).
 * On Desktop: java.awt.Desktop.getDesktop().browse(URI).
 *
 * Failures (no handler, malformed URL) are swallowed silently —
 * callers should consider that a no-op for now.
 */
expect fun openExternalUrl(url: String)
