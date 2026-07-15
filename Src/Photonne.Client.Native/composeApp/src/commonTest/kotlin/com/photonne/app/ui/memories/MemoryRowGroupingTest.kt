package com.photonne.app.ui.memories

import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryKind
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the flat feed becomes rows — including what happens when the server is
 * older than the grouping and sends no theme at all.
 */
class MemoryRowGroupingTest {

    private fun memory(
        id: String,
        kind: MemoryKind,
        title: String,
        themeKey: String = "",
        groupTitle: String = "",
        cardLabel: String? = null,
        year: Int = 2020,
    ): Memory {
        val window = LocalDateTime(year, 6, 1, 12, 0).toInstant(TimeZone.UTC)
        return Memory(
            id = id,
            kind = kind.wire,
            title = title,
            themeKey = themeKey,
            groupTitle = groupTitle,
            cardLabel = cardLabel,
            windowStart = window,
            windowEnd = window,
        )
    }

    @Test
    fun `themed memories fold into one row per theme, years inside`() {
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.CuratedScene, "Días de playa de 2024", "scene:beach", "Días de playa", "2024", year = 2024),
                memory("2", MemoryKind.PetsAndFood, "Tus mascotas en 2023", "object:pets", "Tus mascotas", "2023", year = 2023),
                memory("3", MemoryKind.CuratedScene, "Días de playa de 2021", "scene:beach", "Días de playa", "2021", year = 2021),
            )
        )

        val beach = rows.single { it.key == "scene:beach" }
        assertEquals("Días de playa", beach.title)
        assertEquals(listOf("2024", "2021"), beach.memories.map { it.cardLabel })
        // Titled by the server, so the screen has nothing to resolve.
        assertNull(beach.sectionId)
        assertEquals(2, rows.size)
    }

    /**
     * The server hands these back by score, which interleaves the years. A row of
     * periods is read as a timeline, so out of order it looks broken rather than
     * ranked.
     */
    @Test
    fun `a row of periods runs newest first, not by score`() {
        val rows = groupIntoRows(
            listOf(
                // Score order, exactly as the feed returns it.
                memory("1", MemoryKind.CuratedScene, "Días de playa de 2021", "scene:beach", "Días de playa", "2021", year = 2021),
                memory("2", MemoryKind.CuratedScene, "Días de playa de 2024", "scene:beach", "Días de playa", "2024", year = 2024),
                memory("3", MemoryKind.CuratedScene, "Días de playa de 2019", "scene:beach", "Días de playa", "2019", year = 2019),
                memory("4", MemoryKind.CuratedScene, "Días de playa de 2023", "scene:beach", "Días de playa", "2023", year = 2023),
            )
        )

        assertEquals(listOf("2024", "2023", "2021", "2019"), rows.single().memories.map { it.cardLabel })
    }

    @Test
    fun `the months row runs newest first too`() {
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.ThisMonth, "Julio de 2018", "month", "Este mes", "2018", year = 2018),
                memory("2", MemoryKind.ThisMonth, "Julio de 2023", "month", "Este mes", "2023", year = 2023),
                memory("3", MemoryKind.ThisMonth, "Julio de 2021", "month", "Este mes", "2021", year = 2021),
            )
        )

        assertEquals(listOf("2023", "2021", "2018"), rows.single().memories.map { it.cardLabel })
    }

    /**
     * People are the exception: those cards are people, not periods, so the
     * server's ranking is the point — your daughter before an acquaintance.
     */
    @Test
    fun `the people row keeps the server's ranking`() {
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.PersonThroughYears, "Martina a lo largo de los años", "people", "Personas", "Martina", year = 2019),
                memory("2", MemoryKind.PersonThroughYears, "Joan a lo largo de los años", "people", "Personas", "Joan", year = 2024),
            )
        )

        assertEquals(listOf("Martina", "Joan"), rows.single().memories.map { it.cardLabel })
    }

    @Test
    fun `a person and a pair share the people row`() {
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.PersonThroughYears, "Martina a lo largo de los años", "people", "Personas", "Martina"),
                memory("2", MemoryKind.PeopleTogether, "Martina y Joan", "people", "Personas"),
            )
        )

        val people = rows.single()
        assertEquals("Personas", people.title)
        assertEquals(2, people.memories.size)
    }

    /**
     * The regression behind "sigo viendo una tira de cards sin cabeceras": before
     * this, anything without a theme collapsed into one anonymous row.
     */
    @Test
    fun `an ungrouped feed still falls into named sections`() {
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.CuratedScene, "Días de playa de 2024"),
                memory("2", MemoryKind.PersonThroughYears, "Martina a lo largo de los años"),
                memory("3", MemoryKind.OnThisDay, "Hace 4 años"),
                memory("4", MemoryKind.PetsAndFood, "Tus mascotas en 2023"),
            )
        )

        // Scene and pets both live in Things, so they share its row.
        assertEquals(3, rows.size)
        assertEquals(
            listOf(MemorySectionId.Today, MemorySectionId.People, MemorySectionId.Things),
            rows.map { it.sectionId },
        )
        // The screen titles these from its own strings, so the server's (absent)
        // title must not be what's shown.
        assertTrue(rows.all { it.title.isEmpty() })
    }

    @Test
    fun `rows keep their declared order rather than the server's ranking`() {
        // Handed over in score order, which is what the server actually returns.
        val rows = groupIntoRows(
            listOf(
                memory("1", MemoryKind.CuratedScene, "Días de playa de 2024", "scene:beach", "Días de playa", "2024"),
                memory("2", MemoryKind.OnThisDay, "Hace 4 años", "onthisday", "Hoy"),
                memory("3", MemoryKind.CuratedScene, "Comidas fuera en 2022", "scene:dining", "Comidas fuera", "2022"),
                memory("4", MemoryKind.Trip, "Roma", "trips", "Viajes"),
            )
        )

        // Today, Trips, then Things alphabetically — the order the enum declares,
        // not the order they arrived in.
        assertEquals(listOf("Hoy", "Viajes", "Comidas fuera", "Días de playa"), rows.map { it.title })
    }

    @Test
    fun `kinds this build has never heard of are grouped without a header`() {
        val rows = groupIntoRows(
            listOf(memory("1", MemoryKind.Unknown, "Algo de un servidor más nuevo"))
        )

        val row = rows.single()
        assertEquals(MemorySectionId.Other, row.sectionId)
        assertEquals("", row.title)
    }
}
