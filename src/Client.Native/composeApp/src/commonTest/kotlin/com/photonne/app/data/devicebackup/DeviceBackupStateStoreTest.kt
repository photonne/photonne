package com.photonne.app.data.devicebackup

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multi-folder backup has to inherit what single-folder installs were already
 * backing up: silently losing the folder on upgrade would stop a user's backups
 * without a word.
 */
class DeviceBackupStateStoreTest {

    private val camera = DeviceFolderRef(uri = "content://camera", displayName = "Camera")
    private val whatsapp = DeviceFolderRef(uri = "content://whatsapp", displayName = "WhatsApp")

    @Test
    fun migratesTheLegacySingleFolder() {
        val settings = MapSettings()
        // What a pre-multi-folder install left behind.
        settings.putString(
            "device_backup.folder",
            """{"uri":"content://camera","displayName":"Camera"}"""
        )
        val store = DeviceBackupStateStore(settings)

        assertEquals(listOf(camera), store.savedFolders())
        assertTrue(
            settings.getStringOrNull("device_backup.folder") == null,
            "the legacy key should be consumed, not left to be re-migrated"
        )
    }

    @Test
    fun startsEmptyWithoutAnySavedFolder() {
        assertEquals(emptyList(), DeviceBackupStateStore(MapSettings()).savedFolders())
    }

    @Test
    fun addsFoldersAndIgnoresDuplicates() {
        val store = DeviceBackupStateStore(MapSettings())

        store.addFolder(camera)
        store.addFolder(whatsapp)
        store.addFolder(camera.copy(displayName = "DCIM/Camera"))

        assertEquals(listOf(camera, whatsapp), store.savedFolders())
    }

    @Test
    fun removingAFolderKeepsTheRest() {
        val store = DeviceBackupStateStore(MapSettings())
        store.addFolder(camera)
        store.addFolder(whatsapp)

        store.removeFolder(camera.uri)

        assertEquals(listOf(whatsapp), store.savedFolders())
    }

    @Test
    fun cachedScansAreKeptPerFolder() {
        val store = DeviceBackupStateStore(MapSettings())
        val shot = DeviceMedia(
            uri = "content://camera/1",
            displayName = "IMG_1.jpg",
            relativePath = "",
            mimeType = "image/jpeg",
            sizeBytes = 10L,
            dateModifiedMillis = 1L,
            type = DeviceMediaType.Image
        )

        store.saveCachedMedia(camera.uri, listOf(shot))
        store.saveCachedMedia(whatsapp.uri, emptyList())

        assertEquals(listOf(shot), store.cachedMedia(camera.uri))
        assertEquals(emptyList(), store.cachedMedia(whatsapp.uri))

        // Dropping one folder's cache must not touch the others.
        store.clearCachedMedia(whatsapp.uri)
        assertEquals(listOf(shot), store.cachedMedia(camera.uri))
    }
}
