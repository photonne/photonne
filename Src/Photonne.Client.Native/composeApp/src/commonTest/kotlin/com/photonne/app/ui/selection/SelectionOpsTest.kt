package com.photonne.app.ui.selection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SelectionOpsTest {

    @Test
    fun toggled_adds_when_absent_and_removes_when_present() {
        assertEquals(setOf("a"), emptySet<String>().toggled("a"))
        assertEquals(setOf("a", "b"), setOf("a").toggled("b"))
        assertEquals(setOf("b"), setOf("a", "b").toggled("a"))
        assertEquals(emptySet(), setOf("a").toggled("a"))
    }

    @Test
    fun applying_adds_and_removes_in_one_pass() {
        val base = setOf("a", "b")
        val patch = SelectionPatch(select = listOf("c", "d"), deselect = listOf("a"))
        assertEquals(setOf("b", "c", "d"), base.applying(patch))
    }

    @Test
    fun applying_an_empty_patch_returns_the_same_instance() {
        // A drag frame that doesn't cross a cell must not mint a new state.
        val base = setOf("a")
        assertSame(base, base.applying(SelectionPatch.Empty))
        assertSame(base, base.applying(SelectionPatch()))
    }

    @Test
    fun applying_lets_deselect_win_a_collision() {
        // The band never produces an overlap, but the contract has to be
        // deterministic if a caller ever hands one in.
        val patch = SelectionPatch(select = listOf("a"), deselect = listOf("a"))
        assertEquals(emptySet(), emptySet<String>().applying(patch))
    }

    @Test
    fun with_selection_does_not_toggle() {
        val base = setOf("a", "b")
        // Selecting something already selected is a no-op, not a removal —
        // this is what separates the rail / group checkbox from a tap.
        assertEquals(setOf("a", "b", "c"), base.withSelection(listOf("a", "c"), selected = true))
        assertEquals(setOf("b"), base.withSelection(listOf("a", "z"), selected = false))
        assertSame(base, base.withSelection(emptyList(), selected = true))
    }

    @Test
    fun toggled_all_selects_everything_then_clears() {
        val all = listOf("a", "b", "c")
        assertEquals(setOf("a", "b", "c"), emptySet<String>().toggledAll(all))
        // Partially selected still means "select the rest", not "clear".
        assertEquals(setOf("a", "b", "c"), setOf("a").toggledAll(all))
        assertEquals(emptySet(), setOf("a", "b", "c").toggledAll(all))
    }

    @Test
    fun toggled_all_over_an_empty_list_clears() {
        assertEquals(emptySet(), setOf("a").toggledAll(emptyList()))
    }

    @Test
    fun group_selection_state_is_tri_state() {
        val group = listOf("a", "b", "c")
        assertEquals(GroupSelectionState.None, emptySet<String>().selectionStateOf(group))
        assertEquals(GroupSelectionState.Partial, setOf("b").selectionStateOf(group))
        assertEquals(GroupSelectionState.All, setOf("a", "b", "c").selectionStateOf(group))
        // Ids selected outside the group don't make it "All".
        assertEquals(GroupSelectionState.Partial, setOf("a", "z").selectionStateOf(group))
    }

    @Test
    fun group_selection_state_of_an_empty_group_is_none() {
        assertEquals(GroupSelectionState.None, setOf("a").selectionStateOf(emptyList()))
    }
}
