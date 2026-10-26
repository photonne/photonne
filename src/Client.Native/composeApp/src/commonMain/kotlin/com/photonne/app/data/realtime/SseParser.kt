package com.photonne.app.data.realtime

/**
 * Splits a Server-Sent Events body into the event names it carries, fed
 * chunk by chunk (an event can straddle two). Comments (": ping") and data
 * lines are skipped: our events only say what changed. Bytes are read one to
 * a char: the names are ASCII, so a multi-byte character cut in two can only
 * land in a skipped line.
 */
class SseParser {
    private val line = StringBuilder()
    private var event = ""

    /** The events completed by the first [length] bytes of [chunk], in order. */
    fun push(chunk: ByteArray, length: Int = chunk.size): List<String> {
        val events = ArrayList<String>()
        for (i in 0 until length) {
            val char = (chunk[i].toInt() and 0xFF).toChar()
            if (char != '\n') {
                line.append(char)
                continue
            }
            val text = line.toString().removeSuffix("\r")
            line.clear()
            when {
                text.isEmpty() -> {
                    if (event.isNotEmpty()) events.add(event)
                    event = ""
                }
                text.startsWith("event:") -> event = text.removePrefix("event:").trim()
            }
        }
        return events
    }
}
