package com.photonne.app.data.notifications

/** Escritorio: sin segundo plano; la campana de la app ya lo cuenta. */
actual fun createActivityNotificationScheduler(): ActivityNotificationScheduler =
    object : ActivityNotificationScheduler {}
