package com.photonne.app.ui.util

import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(url))
            }
        }
    }
}
