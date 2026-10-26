package com.photonne.app.data.onboarding

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeStoreTest {
    @Test
    fun `each account sees the welcome once`() {
        val store = WelcomeStore(MapSettings())
        assertTrue(store.shouldShow("u-1"))
        store.markSeen("u-1")
        assertFalse(store.shouldShow("u-1"))
        assertTrue(store.shouldShow("u-2"))
    }
}
