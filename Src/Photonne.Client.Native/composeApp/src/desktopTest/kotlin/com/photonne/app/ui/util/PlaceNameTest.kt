package com.photonne.app.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceNameTest {

    @Test
    fun cityAndCountryInTheAppLanguage() {
        // El idioma depende del de la JVM: español por defecto, inglés si es "en".
        val place = formatPlaceName("Girona", "ES")
        assertTrue(place == "Girona, España" || place == "Girona, Spain", "got $place")
    }

    @Test
    fun cityAloneWhenTheCountryIsUnknown() {
        assertEquals("Girona", formatPlaceName("Girona", null))
        assertEquals("Girona", formatPlaceName("Girona", "ZZZ"))
    }

    @Test
    fun nothingWithoutACity() {
        assertNull(formatPlaceName(null, "ES"))
        assertNull(formatPlaceName("  ", "ES"))
    }
}
