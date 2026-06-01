package com.photonne.app.ui.platform

// Desktop windows are free-form; orientation locking is meaningless here.
actual object OrientationController {
    actual fun allowAutoRotate() {}
    actual fun forceLandscape() {}
    actual fun lockPortrait() {}
}
