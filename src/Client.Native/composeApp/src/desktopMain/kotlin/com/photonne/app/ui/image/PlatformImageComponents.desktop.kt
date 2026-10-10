package com.photonne.app.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    // Desktop's default fetchers cover `file://` URIs from JFileChooser.
}
