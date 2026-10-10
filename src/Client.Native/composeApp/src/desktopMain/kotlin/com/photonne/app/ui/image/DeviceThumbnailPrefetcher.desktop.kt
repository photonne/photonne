package com.photonne.app.ui.image

/** No-op: desktop has no device library (see DeviceLibrary.desktop). */
actual object DeviceThumbnailPrefetcher {
    actual fun setWindow(uris: List<String>) = Unit
}
