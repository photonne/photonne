package com.photonne.app.ui.platform

/**
 * Controls device orientation locking. The app is portrait-only everywhere
 * (AndroidManifest `screenOrientation="portrait"` / iOS Info.plist), but the
 * video viewer relaxes that so a clip can be watched landscape:
 *
 *  - [allowAutoRotate] — while a video is on screen, let the sensor rotate the
 *    app freely (portrait or landscape), like the native galleries.
 *  - [forceLandscape] — the fullscreen button on a landscape clip rotates to
 *    landscape regardless of how the user is holding the phone.
 *  - [lockPortrait] — leaving the video (or the viewer) returns to portrait.
 *
 * Each platform actual maps these onto its own orientation API. Desktop is a
 * no-op (window already free-form).
 */
expect object OrientationController {
    fun allowAutoRotate()
    fun forceLandscape()
    fun lockPortrait()
}
