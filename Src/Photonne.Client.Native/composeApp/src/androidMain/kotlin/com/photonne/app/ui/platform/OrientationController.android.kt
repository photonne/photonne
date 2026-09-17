package com.photonne.app.ui.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import java.lang.ref.WeakReference

/**
 * Android maps orientation control onto the host Activity's
 * `requestedOrientation`. [MainActivity] registers itself via [attach] in
 * onCreate (and clears it in onDestroy) so commonMain can flip orientation
 * without a Context.
 */
actual object OrientationController {
    private var activityRef: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    private fun set(orientation: Int) {
        activityRef?.get()?.requestedOrientation = orientation
    }

    // USER, not SENSOR: SENSOR rotates even with the system's auto-rotate
    // switched off, USER follows the sensor only when the user allows it. (And
    // not FULL_USER, which would let upside-down portrait in.)
    actual fun allowAutoRotate() = set(ActivityInfo.SCREEN_ORIENTATION_USER)

    actual fun forceLandscape() = set(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

    actual fun lockPortrait() = set(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
}
