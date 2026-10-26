package com.photonne.app.data.search

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentSearchStoreTest {
    @Test
    fun keepsTheNewestFirstWithoutRepeatsAndSurvivesARestart() {
        val settings = MapSettings()
        val store = RecentSearchStore(settings)
        store.add(RecentSearch(label = "«playa»", query = "playa"))
        store.add(RecentSearch(label = "#boda", pinned = RecentPinned.of(PinnedSearchFilter.Tag("boda"))))
        // The same search again, even with another label, moves to the front.
        store.add(RecentSearch(label = "«playa» otra vez", query = "playa"))

        assertEquals(listOf("«playa» otra vez", "#boda"), store.recent.value.map { it.label })
        assertEquals(store.recent.value, RecentSearchStore(settings).recent.value)
        assertEquals(PinnedSearchFilter.Tag("boda"), store.recent.value[1].pinned?.toFilter())
    }

    @Test
    fun keepsAtMostEightAndClears() {
        val store = RecentSearchStore(MapSettings())
        repeat(12) { store.add(RecentSearch(label = "q$it", query = "q$it")) }
        assertEquals(RecentSearchStore.MAX, store.recent.value.size)
        assertEquals("q11", store.recent.value.first().label)

        store.clear()
        assertTrue(store.recent.value.isEmpty())
    }

    @Test
    fun ignoresASearchWithoutLabel() {
        val store = RecentSearchStore(MapSettings())
        store.add(RecentSearch(label = " "))
        assertTrue(store.recent.value.isEmpty())
    }
}
