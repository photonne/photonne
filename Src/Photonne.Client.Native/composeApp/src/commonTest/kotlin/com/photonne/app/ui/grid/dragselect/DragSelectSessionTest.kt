package com.photonne.app.ui.grid.dragselect

import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Banco de pruebas: una lista plana "a0".."a9", un conjunto de selección
 * mutable y la sesión aplicando sus parches encima, igual que hará el
 * ViewModel. Comprobar el conjunto resultante (y no los parches sueltos) es lo
 * que valida de verdad la semántica.
 */
private class Bench(
    anchor: Int,
    initiallySelected: Set<String> = emptySet(),
    count: Int = 10,
    private val selectable: (Int) -> Boolean = { true }
) {
    private val ids = (0 until count).map { "a$it" }
    var selection: Set<String> = initiallySelected
        private set
    private val base = initiallySelected

    val session = DragSelectSession(
        anchorOrdinal = anchor,
        mode = modeForAnchor(idsAt(anchor)) { it in base },
        baseSelected = { it in base },
        idsAt = ::idsAt
    )

    private fun idsAt(ordinal: Int): List<String> {
        if (ordinal !in ids.indices || !selectable(ordinal)) return emptyList()
        return listOf(ids[ordinal])
    }

    fun start() { selection = selection.applying(session.start()) }
    fun moveTo(ordinal: Int): SelectionPatch =
        session.moveTo(ordinal).also { selection = selection.applying(it) }
}

class DragSelectSessionTest {

    @Test
    fun an_unselected_anchor_makes_the_band_select() {
        val bench = Bench(anchor = 2)
        assertEquals(DragSelectMode.Select, bench.session.mode)

        bench.start()
        assertEquals(setOf("a2"), bench.selection)

        bench.moveTo(5)
        assertEquals(setOf("a2", "a3", "a4", "a5"), bench.selection)
    }

    @Test
    fun a_selected_anchor_makes_the_band_deselect() {
        // Same gesture, opposite intent: correcting an existing selection
        // without switching tools.
        val bench = Bench(anchor = 2, initiallySelected = setOf("a1", "a2", "a3", "a4"))
        assertEquals(DragSelectMode.Deselect, bench.session.mode)

        bench.start()
        assertEquals(setOf("a1", "a3", "a4"), bench.selection)

        bench.moveTo(4)
        assertEquals(setOf("a1"), bench.selection)
    }

    @Test
    fun the_band_grows_upwards_too() {
        val bench = Bench(anchor = 6)
        bench.start()
        bench.moveTo(3)
        assertEquals(setOf("a3", "a4", "a5", "a6"), bench.selection)
    }

    @Test
    fun backing_off_undoes_what_the_band_had_covered() {
        val bench = Bench(anchor = 2)
        bench.start()
        bench.moveTo(7)
        assertEquals(setOf("a2", "a3", "a4", "a5", "a6", "a7"), bench.selection)

        bench.moveTo(4)
        assertEquals(setOf("a2", "a3", "a4"), bench.selection)
    }

    @Test
    fun a_cell_selected_before_the_gesture_comes_back_when_the_band_recedes() {
        // THE regression this class exists for: overshooting and pulling back
        // must not wipe out selections the user already had.
        val bench = Bench(anchor = 2, initiallySelected = setOf("a6"))
        assertEquals(DragSelectMode.Select, bench.session.mode)

        bench.start()
        bench.moveTo(7)
        assertEquals(setOf("a2", "a3", "a4", "a5", "a6", "a7"), bench.selection)

        bench.moveTo(3)
        // a6 was selected before the drag started, so it survives; a7 doesn't.
        assertEquals(setOf("a2", "a3", "a6"), bench.selection)
    }

    @Test
    fun deselect_mode_restores_the_other_side_of_the_anchor_too() {
        val bench = Bench(anchor = 5, initiallySelected = setOf("a3", "a4", "a5", "a6", "a7"))
        bench.start()
        bench.moveTo(7)
        assertEquals(setOf("a3", "a4"), bench.selection)

        bench.moveTo(5)
        // a6 and a7 were selected before the gesture — they return.
        assertEquals(setOf("a3", "a4", "a6", "a7"), bench.selection)
    }

    @Test
    fun crossing_the_anchor_releases_the_far_side_in_the_same_move() {
        val bench = Bench(anchor = 5)
        bench.start()
        bench.moveTo(8)
        assertEquals(setOf("a5", "a6", "a7", "a8"), bench.selection)

        bench.moveTo(3)
        assertEquals(setOf("a3", "a4", "a5"), bench.selection)
    }

    @Test
    fun standing_still_produces_no_patch() {
        // This is what throttles the haptic tick to one per crossed cell.
        val bench = Bench(anchor = 2)
        bench.start()
        bench.moveTo(4)

        val again = bench.moveTo(4)
        assertSame(SelectionPatch.Empty, again)
        assertTrue(again.isEmpty)
    }

    @Test
    fun unselectable_ordinals_are_skipped_without_breaking_the_range() {
        // Local-only items and skeleton rows: the band still spans them, it
        // just doesn't act on them.
        val bench = Bench(anchor = 1, selectable = { it != 3 })
        bench.start()
        bench.moveTo(5)
        assertEquals(setOf("a1", "a2", "a4", "a5"), bench.selection)

        bench.moveTo(1)
        assertEquals(setOf("a1"), bench.selection)
    }

    @Test
    fun ordinals_past_the_loaded_list_are_ignored() {
        // Auto-scroll can run the band past what's actually loaded.
        val bench = Bench(anchor = 8, count = 10)
        bench.start()
        bench.moveTo(40)
        assertEquals(setOf("a8", "a9"), bench.selection)
    }

    @Test
    fun the_band_tracks_the_pointer_even_when_nothing_changes_hands() {
        val bench = Bench(anchor = 4)
        bench.start()
        bench.moveTo(9)
        assertEquals(4..9, bench.session.band)
        bench.moveTo(0)
        assertEquals(0..4, bench.session.band)
    }

    @Test
    fun an_anchor_already_selected_flips_the_mode() {
        assertEquals(DragSelectMode.Deselect, modeForAnchor(listOf("a")) { it == "a" })
        assertEquals(DragSelectMode.Select, modeForAnchor(listOf("a")) { false })
        // An inert anchor must not read as "already selected".
        assertEquals(DragSelectMode.Select, modeForAnchor(emptyList()) { true })
    }
}
