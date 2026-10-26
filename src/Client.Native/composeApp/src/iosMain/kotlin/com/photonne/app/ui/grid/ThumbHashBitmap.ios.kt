package com.photonne.app.ui.grid

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual fun thumbHashBitmap(image: ThumbHashImage): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(image.w, image.h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(image.rgba)
    return bitmap.asComposeImageBitmap()
}
