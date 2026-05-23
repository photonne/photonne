package com.photonne.app.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    // Android's built-in fetchers already handle the `content://` URIs
    // from the SAF folder picker — no extra registration needed.
}
