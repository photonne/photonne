package com.photonne.app.data.devicelibrary

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The timeline's device-library scope must default to camera-only (the
 * whole point of the preference), survive relaunches, and degrade to
 * that same default — never to "show everything" — when its persisted
 * form is unreadable.
 */
class DeviceLibraryScopeStoreTest {

    @Test
    fun defaultsToCameraOnly() {
        assertEquals(
            DeviceLibraryScope.CameraOnly,
            DeviceLibraryScopeStore(MapSettings()).value.value
        )
    }

    @Test
    fun roundTripsEveryMode() {
        val settings = MapSettings()
        val store = DeviceLibraryScopeStore(settings)

        store.update(DeviceLibraryScope.All)
        assertEquals(DeviceLibraryScope.All, DeviceLibraryScopeStore(settings).value.value)

        store.update(DeviceLibraryScope.Buckets(setOf("12", "34")))
        assertEquals(
            DeviceLibraryScope.Buckets(setOf("12", "34")),
            DeviceLibraryScopeStore(settings).value.value
        )

        store.update(DeviceLibraryScope.CameraOnly)
        assertEquals(
            DeviceLibraryScope.CameraOnly,
            DeviceLibraryScopeStore(settings).value.value
        )
    }

    @Test
    fun corruptBucketListFallsBackToTheDefault() {
        val settings = MapSettings()
        settings.putString("device_library.scope_mode", "buckets")
        settings.putString("device_library.scope_buckets", "not json")

        assertEquals(
            DeviceLibraryScope.CameraOnly,
            DeviceLibraryScopeStore(settings).value.value
        )
    }

    @Test
    fun anEmptyBucketPickIsPreservedNotWidened() {
        val settings = MapSettings()
        DeviceLibraryScopeStore(settings).update(DeviceLibraryScope.Buckets(emptySet()))

        // Explicitly unchecking everything means "show nothing local", and a
        // relaunch must not quietly re-flood the timeline.
        assertEquals(
            DeviceLibraryScope.Buckets(emptySet()),
            DeviceLibraryScopeStore(settings).value.value
        )
    }

    @Test
    fun noticeDismissalPersists() {
        val settings = MapSettings()
        val store = DeviceLibraryScopeStore(settings)
        assertFalse(store.noticeDismissed.value)

        store.dismissNotice()

        assertTrue(store.noticeDismissed.value)
        assertTrue(DeviceLibraryScopeStore(settings).noticeDismissed.value)
    }
}
