package com.photonne.app.ui.platform

/**
 * Controls device orientation locking. The app is portrait-only everywhere
 * (AndroidManifest `screenOrientation="portrait"` / iOS Info.plist), but the
 * asset viewers relax that so a photo or clip can be seen landscape:
 *
 *  - [allowAutoRotate] — while the viewer is on screen, let the phone rotate
 *    the app (portrait or landscape) like the native galleries. It honours the
 *    system rotation lock: with auto-rotate off, turning the phone does nothing.
 *  - [forceLandscape] — the device preview's fullscreen button rotates a clip
 *    to landscape regardless of how the user is holding the phone.
 *  - [lockPortrait] — leaving the viewer returns to portrait.
 *
 * Each platform actual maps these onto its own orientation API. Desktop is a
 * no-op (window already free-form).
 */
expect object OrientationController {
    fun allowAutoRotate()
    fun forceLandscape()
    fun lockPortrait()
}
