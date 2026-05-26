package com.photonne.app.data.devicebackup

import java.net.InetAddress

/** Falls back to the JVM hostname; mostly used in dev/test, not real backup. */
actual fun currentDeviceName(): String {
    return runCatching { InetAddress.getLocalHost().hostName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Desktop"
}
