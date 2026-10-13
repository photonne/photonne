package com.photonne.app.data.settings

import com.photonne.app.data.models.AdminStatsResponse
import com.photonne.app.data.models.AdminUserUsage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El reparto por espacio de Almacenamiento y del Panel: qué suma al total
 * cuando se ocultan las carpetas compartidas o las bibliotecas, y qué pasa con
 * un servidor anterior que no manda el reparto.
 */
class StorageSpacesTest {

    @Test
    fun hiddenSpacesLeaveTheTotalButPersonalAlwaysCounts() {
        val bytes = SpaceBytes(personal = 10, shared = 200, library = 3000)

        assertEquals(3210, bytes.visible(emptySet()))
        assertEquals(3010, bytes.visible(setOf(StorageSpace.Shared)))
        assertEquals(10, bytes.visible(StorageSpace.entries.toSet()))
    }

    @Test
    fun storedPreferenceDropsUnknownNamesAndPersonal() {
        assertEquals(setOf(StorageSpace.Shared, StorageSpace.Library), parseHiddenSpaces("Shared, Library"))
        assertEquals(setOf(StorageSpace.Shared), parseHiddenSpaces("Personal,Trash,Shared"))
        assertEquals(emptySet(), parseHiddenSpaces(null))
    }

    @Test
    fun olderServerWithoutTheSplitCountsEverythingAsPersonal() {
        val user = AdminUserUsage(userId = "u", displayName = "Ana", photoBytes = 700, videoBytes = 300)
        assertEquals(SpaceBytes(personal = 1000), user.spaceBytes())

        val stats = AdminStatsResponse(totalBytes = 1000)
        assertEquals(SpaceBytes(personal = 1000), stats.spaceBytes())
    }

    @Test
    fun newerServerSplitIsTakenAsIs() {
        val user = AdminUserUsage(
            userId = "u", displayName = "Ana", photoBytes = 700, videoBytes = 300,
            personalBytes = 100, sharedBytes = 400, libraryBytes = 500
        )
        assertEquals(SpaceBytes(100, 400, 500), user.spaceBytes())
    }
}
