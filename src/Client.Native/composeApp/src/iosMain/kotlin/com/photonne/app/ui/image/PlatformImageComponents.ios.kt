package com.photonne.app.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    // PhotoKit fetcher resolves `photokit:` URIs for the Backup grid; it also
    // returns a poster frame for video assets, so no video decoder is needed.
    add(PhotoKitImageFetcher.Factory())
}
