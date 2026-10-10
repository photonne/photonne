package com.photonne.app.ui.image

/**
 * Viewport-driven warm-up for DEVICE thumbnails. The timeline reports
 * the local-item URIs of the buckets on or near the viewport whenever
 * scrolling settles; the platform keeps exactly that window warm so
 * cells paint from cache instead of paying a cold platform request on
 * first composition.
 *
 * - **iOS** forwards the window to the shared [PHCachingImageManager]
 *   behind the PhotoKit fetcher (start caching added assets, stop
 *   caching removed ones) — PhotoKit decodes ahead of the scroll.
 * - **Android/desktop** are no-ops: MediaStore's `loadThumbnail` reads
 *   the system's precomputed thumbnail cache, already viewport-speed.
 *
 * Call from a background dispatcher — the platform may hit its media
 * database to resolve URIs. Calls are idempotent and cheap when the
 * window hasn't changed.
 */
expect object DeviceThumbnailPrefetcher {
    /** Replaces the warm window with [uris] (non-device URIs ignored). */
    fun setWindow(uris: List<String>)
}
