package com.photonne.app.ui.timeline

import com.photonne.app.data.timeline.TimelineBucketState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class JumpMonthsTest {
    @Test
    fun groupsTheSkeletonIntoYearsNewestFirstWithAllTwelveMonths() {
        val years = jumpYears(
            listOf(
                TimelineBucketState("2025-02", 3),
                TimelineBucketState("2024-12", 10),
                TimelineBucketState("2025-11", 7),
                TimelineBucketState("2024-01", 0),
                TimelineBucketState("2023-05", 0)
            )
        )

        assertEquals(listOf(2025, 2024), years.map { it.year })
        assertEquals(10, years[0].total)
        assertEquals(12, years[0].months.size)
        assertEquals(3, years[0].months[1])
        assertEquals(7, years[0].months[10])
        assertEquals(10, years[1].months[11])
    }

    @Test
    fun landsOnTheLastDayOfTheMonth() {
        assertEquals(Instant.parse("2024-02-29T00:00:00Z"), jumpTarget(2024, 2))
        assertEquals(Instant.parse("2025-12-31T00:00:00Z"), jumpTarget(2025, 12))
    }
}
