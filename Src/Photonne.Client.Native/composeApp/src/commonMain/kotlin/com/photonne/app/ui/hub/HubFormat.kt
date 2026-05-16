package com.photonne.app.ui.hub

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Localized human-readable byte size: 1.2 GB / 845 MB / 22 KB. */
internal fun humanBytes(bytes: Long): String {
    val abs = abs(bytes)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = abs.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    val rounded = when {
        value >= 100 -> ((value * 1).toLong()).toString()
        value >= 10 -> ((value * 10).toLong() / 10.0).format1()
        else -> ((value * 10).toLong() / 10.0).format1()
    }
    val sign = if (bytes < 0) "-" else ""
    return "$sign$rounded ${units[idx]}"
}

private fun Double.format1(): String {
    val whole = toLong()
    val frac = ((this - whole) * 10).toLong()
    return if (frac == 0L) whole.toString() else "$whole.$frac"
}

/** Thousand-separated integer count: 1.234 / 12.345. */
internal fun formatCount(value: Int): String {
    val s = value.toString()
    val sb = StringBuilder()
    var c = 0
    for (i in s.lastIndex downTo 0) {
        sb.append(s[i])
        c++
        if (c == 3 && i != 0) {
            sb.append('.')
            c = 0
        }
    }
    return sb.reverse().toString()
}

internal enum class GreetingSlot { Morning, Afternoon, Evening }

/** Picks morning/afternoon/evening based on the user's local clock. */
internal fun currentGreetingSlot(): GreetingSlot {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when {
        hour in 6..12 -> GreetingSlot.Morning
        hour in 13..20 -> GreetingSlot.Afternoon
        else -> GreetingSlot.Evening
    }
}
