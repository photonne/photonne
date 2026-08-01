package com.photonne.app.ui.format

/**
 * 1024-based human-readable byte size. Lives in a neutral package because
 * admin, utilities and backup all report sizes and they should read the same.
 */
internal fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = ((value * 100).toLong()).toDouble() / 100.0
    return "$formatted ${units[unitIndex]}"
}
