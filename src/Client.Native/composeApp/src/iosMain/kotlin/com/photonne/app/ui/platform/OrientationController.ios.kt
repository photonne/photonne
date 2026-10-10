package com.photonne.app.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

/**
 * iOS orientation control. iOS asks the app delegate which orientations are
 * supported via `application(_:supportedInterfaceOrientationsFor:)`; the
 * delegate reads [currentMask] (exposed to Swift through [IosOrientationBridge]).
 * After changing the mask we (a) tell the active window scene to update its
 * supported orientations and (b) request an immediate geometry update so the
 * UI rotates without waiting for the user to physically turn the device — this
 * is what makes the fullscreen button rotate the clip on its own.
 */
@OptIn(ExperimentalForeignApi::class)
actual object OrientationController {

    actual fun allowAutoRotate() = apply(UIInterfaceOrientationMaskAllButUpsideDown)

    actual fun forceLandscape() = apply(UIInterfaceOrientationMaskLandscape)

    actual fun lockPortrait() = apply(UIInterfaceOrientationMaskPortrait)

    private fun apply(mask: ULong) {
        IosOrientationBridge.currentMask = mask
        val scene = activeWindowScene() ?: return
        // Tell UIKit to re-query supportedInterfaceOrientations on the delegate.
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        // Then force the geometry to honour the new mask right away.
        val prefs = UIWindowSceneGeometryPreferencesIOS(mask)
        scene.requestGeometryUpdateWithPreferences(prefs, errorHandler = null)
    }

    private fun activeWindowScene(): UIWindowScene? {
        // connectedScenes bridges to a Kotlin Set<*>; pick the first UIWindowScene.
        val scenes = UIApplication.sharedApplication.connectedScenes
        return scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    }
}

/**
 * Holds the current orientation mask so `AppDelegate.swift` can return it from
 * `application(_:supportedInterfaceOrientationsFor:)`. Defaults to portrait,
 * matching the Info.plist baseline.
 */
object IosOrientationBridge {
    // UIInterfaceOrientationMaskPortrait == 1UL << 1 == 2.
    var currentMask: ULong = UIInterfaceOrientationMaskPortrait
}
