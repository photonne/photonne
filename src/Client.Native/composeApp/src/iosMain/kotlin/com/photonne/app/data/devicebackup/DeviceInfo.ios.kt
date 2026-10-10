package com.photonne.app.data.devicebackup

import platform.UIKit.UIDevice

/** The user-assigned device name from iOS settings (e.g. "Marc's iPhone"). */
actual fun currentDeviceName(): String {
    val raw = UIDevice.currentDevice.name
    return raw.ifBlank { "iOS" }
}
