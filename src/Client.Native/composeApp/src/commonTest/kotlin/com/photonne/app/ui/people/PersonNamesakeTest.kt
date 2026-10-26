package com.photonne.app.ui.people

import com.photonne.app.data.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonNamesakeTest {
    private val people = listOf(
        Person(id = "p-1", name = "Martí Puig"),
        Person(id = "p-2", name = "Laia"),
        Person(id = "p-3", name = null)
    )

    @Test
    fun `the same name without case or accents is a namesake`() {
        assertEquals("p-1", namesake(people, "  marti  puig ", exceptId = "p-9")?.id)
    }

    @Test
    fun `the person being renamed is not its own namesake`() {
        assertNull(namesake(people, "Laia", exceptId = "p-2"))
    }

    @Test
    fun `a different or empty name finds nobody`() {
        assertNull(namesake(people, "Laura", exceptId = "p-9"))
        assertNull(namesake(people, "   ", exceptId = "p-9"))
    }
}
