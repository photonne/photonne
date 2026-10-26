package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification

/**
 * Charging from UIDevice's battery state, followed while the screen is shown.
 * The network kind stays unknown: the screen then names the Wi-Fi preference
 * as before rather than guess.
 */
@Composable
actual fun rememberDeviceConditions(): DeviceConditions {
    var charging by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(Unit) {
        val device = UIDevice.currentDevice
        val wasMonitoring = device.batteryMonitoringEnabled
        device.batteryMonitoringEnabled = true
        charging = device.isCharging()
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceBatteryStateDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> charging = device.isCharging() }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            device.batteryMonitoringEnabled = wasMonitoring
        }
    }
    return DeviceConditions(onUnmeteredNetwork = null, charging = charging)
}

private fun UIDevice.isCharging(): Boolean? = when (batteryState) {
    UIDeviceBatteryState.UIDeviceBatteryStateCharging,
    UIDeviceBatteryState.UIDeviceBatteryStateFull -> true
    UIDeviceBatteryState.UIDeviceBatteryStateUnplugged -> false
    else -> null
}
