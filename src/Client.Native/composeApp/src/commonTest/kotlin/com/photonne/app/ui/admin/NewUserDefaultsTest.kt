package com.photonne.app.ui.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class NewUserDefaultsTest {

    @Test
    fun unset_settings_fall_back_like_the_server() {
        assertEquals(
            NewUserDefaults(isAdmin = false, isActive = true, quotaMb = ""),
            newUserDefaultsFrom(emptyMap())
        )
        assertEquals(
            NewUserDefaults(isAdmin = false, isActive = true, quotaMb = ""),
            newUserDefaultsFrom(NewUserDefaultKeys.associateWith { "" })
        )
    }

    @Test
    fun configured_defaults_reach_the_form() {
        val defaults = newUserDefaultsFrom(
            mapOf(
                "UserSettings.DefaultRole" to "Admin",
                "UserSettings.DefaultIsActive" to "false",
                "UserSettings.DefaultStorageQuotaGb" to "5",
            )
        )
        assertEquals(NewUserDefaults(isAdmin = true, isActive = false, quotaMb = "5120"), defaults)
    }

    @Test
    fun a_zero_or_unreadable_quota_means_no_quota() {
        assertEquals("", newUserDefaultsFrom(mapOf("UserSettings.DefaultStorageQuotaGb" to "0")).quotaMb)
        assertEquals("", newUserDefaultsFrom(mapOf("UserSettings.DefaultStorageQuotaGb" to "abc")).quotaMb)
    }
}
