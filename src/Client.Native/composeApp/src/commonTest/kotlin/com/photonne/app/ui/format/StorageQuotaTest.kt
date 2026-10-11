package com.photonne.app.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageQuotaTest {

    @Test
    fun no_quota_has_no_fraction() {
        assertNull(storageQuotaFraction(500, null))
        assertNull(storageQuotaFraction(500, 0))
    }

    @Test
    fun the_fraction_is_not_capped_at_the_quota() {
        assertEquals(0.5f, storageQuotaFraction(50, 100))
        assertEquals(1.25f, storageQuotaFraction(125, 100))
    }

    @Test
    fun levels_follow_the_web_thresholds() {
        assertEquals(StorageQuotaLevel.Normal, storageQuotaLevel(0.89f))
        assertEquals(StorageQuotaLevel.Warning, storageQuotaLevel(STORAGE_QUOTA_WARNING_FRACTION))
        // Exactly full is not over the quota yet.
        assertEquals(StorageQuotaLevel.Warning, storageQuotaLevel(1f))
        assertEquals(StorageQuotaLevel.Exceeded, storageQuotaLevel(1.01f))
    }
}
