package com.photonne.app.ui.image

import coil3.ComponentRegistry
import coil3.video.VideoFrameDecoder

/**
 * Android plugs in two components:
 * - [MediaStoreThumbnailFetcher] serves `content://media/...` cells from
 *   the system's precomputed thumbnail cache (API 29+), like the stock
 *   gallery — registered fetchers win over Coil's built-in content one.
 * - Coil's [VideoFrameDecoder] so a video URI that falls through to the
 *   generic stream path (SAF `content://` from the Backup grid, or a
 *   thumbnail-cache miss) still renders its poster frame instead of the
 *   generic video glyph.
 */
actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(MediaStoreThumbnailFetcher.Factory())
    add(VideoFrameDecoder.Factory())
}
