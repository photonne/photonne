package com.photonne.app.data.devicebackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Unmetered network (what WorkManager's NetworkType.UNMETERED waits for) from
 * the default network's capabilities, and charging from the sticky battery
 * broadcast; both followed while the screen is shown.
 */
@Composable
actual fun rememberDeviceConditions(): DeviceConditions {
    val context = LocalContext.current
    var unmetered by remember { mutableStateOf(context.isOnUnmeteredNetwork()) }
    var charging by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }

            override fun onLost(network: Network) {
                unmetered = false
            }
        }
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                charging = intent.isCharging()
            }
        }
        // Sticky: registering hands back the current state at once.
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        charging = sticky?.isCharging()

        onDispose {
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return DeviceConditions(onUnmeteredNetwork = unmetered, charging = charging)
}

private fun Context.isOnUnmeteredNetwork(): Boolean? {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return null
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

private fun Intent.isCharging(): Boolean {
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}
