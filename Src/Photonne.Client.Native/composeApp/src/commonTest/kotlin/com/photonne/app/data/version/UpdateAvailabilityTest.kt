package com.photonne.app.data.version

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateAvailabilityTest {

    @Test
    fun newer_server_versions_are_detected() {
        assertTrue(isNewerVersion("1.112.0", "1.111.1"))
        assertTrue(isNewerVersion("2.0.0", "1.999.999"))
        assertTrue(isNewerVersion("1.112.1", "1.112.0"))
    }

    @Test
    fun equal_or_older_server_versions_are_not_an_update() {
        assertFalse(isNewerVersion("1.112.0", "1.112.0"))
        assertFalse(isNewerVersion("1.111.9", "1.112.0"))
        assertFalse(isNewerVersion(null, "1.112.0"))
    }

    @Test
    fun prefixes_suffixes_and_short_versions_are_tolerated() {
        assertTrue(isNewerVersion("v1.113.0", "1.112.0"))
        assertTrue(isNewerVersion("1.113.0-beta", "1.112.0"))
        assertTrue(isNewerVersion("1.113", "1.112.5"))
        assertFalse(isNewerVersion("garbage", "1.112.0"))
    }
}
