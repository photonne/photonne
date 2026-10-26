package com.photonne.app.data.devicebackup

import kotlin.test.Test
import kotlin.test.assertEquals

class UnmetConditionsTest {
    @Test
    fun `only the preferences the device doesn't meet are waited for`() {
        val onWifiUnplugged = DeviceConditions(onUnmeteredNetwork = true, charging = false)
        assertEquals(
            UnmetConditions(wifi = false, charging = true),
            unmetConditions(requireWifi = true, requireCharging = true, now = onWifiUnplugged)
        )
        assertEquals(
            UnmetConditions(wifi = false, charging = false),
            unmetConditions(requireWifi = true, requireCharging = false, now = onWifiUnplugged)
        )
    }

    @Test
    fun `unknown counts as unmet`() {
        assertEquals(
            UnmetConditions(wifi = true, charging = false),
            unmetConditions(requireWifi = true, requireCharging = false, now = DeviceConditions())
        )
    }
}
