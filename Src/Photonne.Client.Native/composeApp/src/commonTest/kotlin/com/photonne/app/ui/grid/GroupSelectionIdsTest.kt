package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.selection.GroupSelectionState
import com.photonne.app.ui.selection.selectionStateOf
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private fun asset(id: String, isoDate: String, localUri: String? = null) = TimelineItem(
    id = id,
    fileName = "$id.jpg",
    fullPath = "/photos/$id.jpg",
    fileSize = 1L,
    fileCreatedAt = Instant.parse(isoDate),
    fileModifiedAt = Instant.parse(isoDate),
    extension = ".jpg",
    scannedAt = Instant.parse(isoDate),
    type = "IMAGE",
    localUri = localUri
)

/** Rows the month band's checkbox acts on, built through the real packer. */
private fun segmentsOf(items: List<TimelineItem>, columns: Int = 3): List<RowSegment> {
    val entries = groupTimelineEntries(items)
    // Width chosen so columnCountFor() lands exactly on [columns]:
    // n·minCell + (n-1)·spacing, with minCell 110 and spacing 2.
    val width = columns * 110f + (columns - 1) * 2f
    return segmentRows(packUniformRows(entries, width, 110f, 2f))
}

class GroupSelectionIdsTest {

    @Test
    fun collects_every_asset_of_the_group_across_its_rows() {
        // 4 assets in May over 3 columns → two rows in the same segment; the
        // checkbox must reach the trailing partial row too.
        val segments = segmentsOf(
            listOf(
                asset("a", "2026-05-09T10:00:00Z"),
                asset("b", "2026-05-08T10:00:00Z"),
                asset("c", "2026-05-07T10:00:00Z"),
                asset("d", "2026-05-06T10:00:00Z")
            )
        )

        assertEquals(1, segments.size)
        assertEquals(listOf("a", "b", "c", "d"), selectableIdsOf(segments.single()))
    }

    @Test
    fun each_group_only_covers_its_own_month() {
        val segments = segmentsOf(
            listOf(
                asset("a", "2026-05-09T10:00:00Z"),
                asset("b", "2026-04-30T10:00:00Z"),
                asset("c", "2026-04-01T10:00:00Z")
            )
        )

        assertEquals(2, segments.size)
        assertEquals(listOf("a"), selectableIdsOf(segments[0]))
        assertEquals(listOf("b", "c"), selectableIdsOf(segments[1]))
    }

    @Test
    fun local_only_assets_are_left_out() {
        // Device photos still waiting to upload aren't part of the timeline's
        // bulk operations, so the group checkbox must not claim them either.
        val segments = segmentsOf(
            listOf(
                asset("a", "2026-05-09T10:00:00Z"),
                asset("pending", "2026-05-08T10:00:00Z", localUri = "content://media/1"),
                asset("b", "2026-05-07T10:00:00Z")
            )
        )

        assertEquals(listOf("a", "b"), selectableIdsOf(segments.single()))
    }

    @Test
    fun a_group_of_only_local_assets_has_nothing_to_select() {
        // The header then renders without a checkbox at all.
        val segments = segmentsOf(
            listOf(asset("pending", "2026-05-09T10:00:00Z", localUri = "content://media/1"))
        )

        assertEquals(emptyList(), selectableIdsOf(segments.single()))
    }

    @Test
    fun checkbox_state_tracks_the_group_not_the_whole_selection() {
        val segments = segmentsOf(
            listOf(
                asset("a", "2026-05-09T10:00:00Z"),
                asset("b", "2026-05-08T10:00:00Z"),
                asset("c", "2026-04-30T10:00:00Z")
            )
        )
        val may = selectableIdsOf(segments[0])
        val april = selectableIdsOf(segments[1])

        // Selecting all of April leaves May's checkbox empty.
        assertEquals(GroupSelectionState.None, setOf("c").selectionStateOf(may))
        assertEquals(GroupSelectionState.All, setOf("c").selectionStateOf(april))
        assertEquals(GroupSelectionState.Partial, setOf("a", "c").selectionStateOf(may))
        assertEquals(GroupSelectionState.All, setOf("a", "b", "c").selectionStateOf(may))
    }
}
