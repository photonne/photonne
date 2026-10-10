package com.photonne.app.data.devicebackup

import android.os.Build

/** "Pixel 8 Pro", "Samsung SM-G998B", etc. */
actual fun currentDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    return when {
        manufacturer.isEmpty() && model.isEmpty() -> "Android"
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}
