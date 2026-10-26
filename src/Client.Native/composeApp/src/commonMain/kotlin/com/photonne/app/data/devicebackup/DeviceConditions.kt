package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable

/**
 * What the automatic backup waits for, as the device is right now. Null when
 * the platform can't tell: the screen then falls back to naming the
 * preference ("se subirá cuando haya Wi-Fi") instead of claiming it's unmet.
 */
data class DeviceConditions(
    val onUnmeteredNetwork: Boolean? = null,
    val charging: Boolean? = null,
)

/** The current [DeviceConditions], updated while the screen is shown. */
@Composable
expect fun rememberDeviceConditions(): DeviceConditions

/**
 * The automatic backup's preferences that the device doesn't meet right now
 * (or might not: unknown counts as unmet). Both false = nothing to wait for.
 */
data class UnmetConditions(val wifi: Boolean, val charging: Boolean)

fun unmetConditions(requireWifi: Boolean, requireCharging: Boolean, now: DeviceConditions) = UnmetConditions(
    wifi = requireWifi && now.onUnmeteredNetwork != true,
    charging = requireCharging && now.charging != true,
)
