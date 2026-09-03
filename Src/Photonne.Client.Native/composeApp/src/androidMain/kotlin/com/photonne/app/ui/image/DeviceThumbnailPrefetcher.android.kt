package com.photonne.app.ui.image

/** No-op: MediaStore's `loadThumbnail` serves the system's precomputed
 *  thumbnail cache, which is already viewport-speed without warm-up. */
actual object DeviceThumbnailPrefetcher {
    actual fun setWindow(uris: List<String>) = Unit
}
