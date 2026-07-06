package com.photonne.app.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NaturalSortTest {

    @Test
    fun sortsYearsAscending() {
        val input = listOf("2001", "2023", "2006", "2008", "2026")
        assertEquals(
            listOf("2001", "2006", "2008", "2023", "2026"),
            input.sortedByNatural { it }
        )
    }

    @Test
    fun sortsMixedNumbersNumericallyNotLexicographically() {
        val input = listOf("Viaje 10", "Viaje 2", "viaje 1")
        assertEquals(
            listOf("viaje 1", "Viaje 2", "Viaje 10"),
            input.sortedByNatural { it }
        )
    }

    @Test
    fun isCaseInsensitiveForLetters() {
        val input = listOf("Banana", "apple", "Cherry", "avocado")
        assertEquals(
            listOf("apple", "avocado", "Banana", "Cherry"),
            input.sortedByNatural { it }
        )
    }

    @Test
    fun handlesLeadingZerosByMagnitude() {
        // Magnitude wins first (1 < 9 < 10); equal magnitudes ("1" vs "0001")
        // tie-break deterministically with the shorter raw run first.
        val input = listOf("img009", "img10", "img1", "img0001")
        assertEquals(
            listOf("img1", "img0001", "img009", "img10"),
            input.sortedByNatural { it }
        )
    }

    @Test
    fun handlesMultipleNumericSegments() {
        val input = listOf("v1.10", "v1.2", "v1.1", "v2.0")
        assertEquals(
            listOf("v1.1", "v1.2", "v1.10", "v2.0"),
            input.sortedByNatural { it }
        )
    }

    @Test
    fun shorterPrefixSortsBeforeLonger() {
        val input = listOf("Trip", "Trip 2024", "Trip 2024 part 2")
        assertEquals(
            listOf("Trip", "Trip 2024", "Trip 2024 part 2"),
            input.sortedByNatural { it }
        )
    }
}
