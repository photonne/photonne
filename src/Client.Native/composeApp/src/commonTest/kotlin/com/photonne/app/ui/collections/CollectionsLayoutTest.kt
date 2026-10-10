package com.photonne.app.ui.collections

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionsLayoutTest {

    private fun List<CollectionSection>.albumsBeforeFolders() =
        indexOf(CollectionSection.Albums) < indexOf(CollectionSection.Folders)

    @Test
    fun memoriesAndPinnedAlwaysLead() {
        val order = adaptiveSectionOrder(albumCount = 0, personalFolderCount = 5, sharedFolderCount = 0)
        assertEquals(listOf(CollectionSection.Memories, CollectionSection.Pinned), order.take(2))
        assertEquals(CollectionSection.entries.toSet(), order.toSet())
    }

    @Test
    fun albumUserGetsAlbumsFirst() {
        assertTrue(adaptiveSectionOrder(albumCount = 12, personalFolderCount = 3, sharedFolderCount = 1).albumsBeforeFolders())
    }

    @Test
    fun folderUserWithoutAlbumsGetsFoldersFirst() {
        assertFalse(adaptiveSectionOrder(albumCount = 0, personalFolderCount = 8, sharedFolderCount = 0).albumsBeforeFolders())
    }

    @Test
    fun sharedFolderConsumerGetsFoldersFirst() {
        assertFalse(adaptiveSectionOrder(albumCount = 1, personalFolderCount = 0, sharedFolderCount = 4).albumsBeforeFolders())
    }

    @Test
    fun pendingOrganizeBringsFoldersUp() {
        assertFalse(
            adaptiveSectionOrder(albumCount = 10, personalFolderCount = 2, sharedFolderCount = 0, organizePendingCount = 3)
                .albumsBeforeFolders()
        )
    }

    @Test
    fun emptyLibraryKeepsAlbumsFirst() {
        assertTrue(adaptiveSectionOrder(albumCount = 0, personalFolderCount = 0, sharedFolderCount = 0).albumsBeforeFolders())
    }

    @Test
    fun storeCustomizesFromTheVisibleOrderAndPersists() {
        val settings = MapSettings()
        val store = CollectionsLayoutStore(settings)
        val adaptive = adaptiveSectionOrder(albumCount = 0, personalFolderCount = 3, sharedFolderCount = 0)
        assertFalse(store.layout.value.customized)

        store.move(CollectionSection.Albums, -1, adaptive)
        store.setHidden(CollectionSection.Map, true, store.layout.value.fullOrder(adaptive))

        val reloaded = CollectionsLayoutStore(settings).layout.value
        assertTrue(reloaded.customized)
        assertTrue(reloaded.order.albumsBeforeFolders())
        assertFalse(CollectionSection.Map in reloaded.visibleSections(adaptive))
        // Una vez personalizado, el orden adaptativo deja de moverlo.
        val otherAdaptive = adaptiveSectionOrder(albumCount = 9, personalFolderCount = 0, sharedFolderCount = 0)
        assertEquals(reloaded.order.filterNot { it == CollectionSection.Map }, reloaded.visibleSections(otherAdaptive))

        store.reset()
        assertFalse(CollectionsLayoutStore(settings).layout.value.customized)
    }

    @Test
    fun moveAtTheEdgesIsANoOp() {
        val store = CollectionsLayoutStore(MapSettings())
        val adaptive = adaptiveSectionOrder(albumCount = 1, personalFolderCount = 0, sharedFolderCount = 0)
        store.move(CollectionSection.Memories, -1, adaptive)
        assertFalse(store.layout.value.customized)
    }

    @Test
    fun sectionsAddedLaterAppendToASavedOrderAndRemovedOnesDrop() {
        // Un orden guardado con Escenas y Objetos (que ya no son secciones) y
        // sin Favoritos ni Explorar (que aún no existían).
        val settings = MapSettings()
        settings.putBoolean("photonne.collections.customized", true)
        settings.putString("photonne.collections.order", "Map,Memories,Pinned,People,Albums,Folders,Scenes,Objects")
        val order = CollectionsLayoutStore(settings).layout.value.order
        assertEquals(CollectionSection.Map, order.first())
        assertEquals(CollectionSection.entries.toSet(), order.toSet())
        assertEquals(
            listOf(CollectionSection.Favorites, CollectionSection.Explore),
            order.takeLast(2)
        )
    }
}
