package com.photonne.app.ui.grid

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun thumbHashBitmap(image: ThumbHashImage): ImageBitmap {
    val colors = IntArray(image.w * image.h) { i ->
        val o = i * 4
        val r = image.rgba[o].toInt() and 0xFF
        val g = image.rgba[o + 1].toInt() and 0xFF
        val b = image.rgba[o + 2].toInt() and 0xFF
        val a = image.rgba[o + 3].toInt() and 0xFF
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(colors, image.w, image.h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
