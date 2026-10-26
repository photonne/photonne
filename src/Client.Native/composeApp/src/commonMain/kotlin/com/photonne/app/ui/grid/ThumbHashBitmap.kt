package com.photonne.app.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/** [image]'s pixels as an [ImageBitmap] (Android Bitmap / Skia raster). */
expect fun thumbHashBitmap(image: ThumbHashImage): ImageBitmap

// A grid recomposes the same cells as it scrolls: each hash is decoded once.
// Bounded (32×32 px each, ~4 KB) and oldest-first, like the web's cache.
private const val CACHE_SIZE = 600
private val cache = LinkedHashMap<String, ImageBitmap?>()

/** The blurred sketch of [hash] (base64 ThumbHash), or null when there is none. */
fun thumbHashBitmapOf(hash: String?): ImageBitmap? {
    if (hash.isNullOrEmpty()) return null
    if (cache.containsKey(hash)) return cache[hash]
    val bitmap = ThumbHashDecoder.decodeBase64(hash)?.let { runCatching { thumbHashBitmap(it) }.getOrNull() }
    if (cache.size >= CACHE_SIZE) cache.remove(cache.keys.first())
    cache[hash] = bitmap
    return bitmap
}

@Composable
fun rememberThumbHashBitmap(hash: String?): ImageBitmap? = remember(hash) { thumbHashBitmapOf(hash) }
