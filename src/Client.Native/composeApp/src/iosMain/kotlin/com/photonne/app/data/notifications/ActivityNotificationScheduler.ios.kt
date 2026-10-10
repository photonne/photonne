package com.photonne.app.data.notifications

/**
 * iOS: pendiente. La única tarea en segundo plano es la BGProcessingTask del
 * backup (solo con el backup activo y cuando iOS quiere); avisar de actividad
 * pediría una BGAppRefreshTask propia (identificador en Info.plist + registro
 * en AppDelegate) y notificaciones locales con UNUserNotificationCenter.
 */
actual fun createActivityNotificationScheduler(): ActivityNotificationScheduler =
    object : ActivityNotificationScheduler {}
