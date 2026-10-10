package com.photonne.app.data.devicebackup

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

actual suspend fun <T> withBackgroundExecution(block: suspend () -> T): T {
    val app = UIApplication.sharedApplication
    var taskId = UIBackgroundTaskInvalid
    // The expiration handler must end the task or iOS kills the app outright.
    taskId = app.beginBackgroundTaskWithName("com.photonne.app.upload") {
        if (taskId != UIBackgroundTaskInvalid) {
            app.endBackgroundTask(taskId)
            taskId = UIBackgroundTaskInvalid
        }
    }
    return try {
        block()
    } finally {
        if (taskId != UIBackgroundTaskInvalid) {
            app.endBackgroundTask(taskId)
            taskId = UIBackgroundTaskInvalid
        }
    }
}
