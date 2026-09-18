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

    /**
     * El bloqueo vertical solo aplica a anchos compactos (punto 49 del
     * roadmap): en tablets (sw >= 600 dp) la app rota libre. El manifiesto
     * arranca en portrait para evitar el parpadeo del primer frame; aquí se
     * libera nada más crear la Activity.
     */
    private var lockToPortrait = true

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        lockToPortrait =
            activity.resources.configuration.smallestScreenWidthDp < 600
        if (!lockToPortrait) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
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

    actual fun lockPortrait() = set(
        if (lockToPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_USER
    )
}
