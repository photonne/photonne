package com.photonne.app.ui.image

import coil3.ComponentRegistry
import coil3.video.VideoFrameDecoder

/**
 * Android registers Coil's [VideoFrameDecoder] so a `content://` / `file://`
 * video URI renders its poster frame. The built-in fetchers already handle
 * image URIs; without this decoder a device video pending upload fell back to
 * the generic video glyph. Photos are unaffected.
 */
actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(VideoFrameDecoder.Factory())
}
