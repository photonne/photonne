package com.photonne.app.ui.admin

import com.photonne.app.data.models.NO_STORAGE_QUOTA
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

    @Test
    fun a_new_account_quota_is_the_default_unless_the_admin_changed_it() {
        val mb = 1024L * 1024L
        // Untouched: the field shows the default and the server applies it.
        assertEquals(null, newUserQuotaBytes("5120", touched = false))
        assertEquals(null, newUserQuotaBytes("", touched = false))
        // Emptied on purpose: no quota, even with a default.
        assertEquals(NO_STORAGE_QUOTA, newUserQuotaBytes("", touched = true))
        assertEquals(NO_STORAGE_QUOTA, newUserQuotaBytes("0", touched = true))
        assertEquals(2048 * mb, newUserQuotaBytes("2048", touched = true))
    }
}
