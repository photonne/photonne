package com.photonne.app.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(PhotoKitImageFetcher.Factory())
}
