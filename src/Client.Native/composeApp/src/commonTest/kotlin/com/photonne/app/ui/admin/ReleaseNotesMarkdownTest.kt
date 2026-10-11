package com.photonne.app.ui.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseNotesMarkdownTest {

    @Test
    fun a_release_please_changelog_becomes_headings_and_items() {
        val notes = """
            ## [1.196.0](https://github.com/x/photonne/compare/v1.195.0...v1.196.0) (2026-10-10)

            ### Features

            * **spa:** El buscador busca con IA ([#12](https://github.com/x/photonne/issues/12)) ([261bdcc](https://github.com/x/photonne/commit/261bdcc))
            * Otra cosa
        """.trimIndent()

        val blocks = parseReleaseNotes(notes)

        assertEquals(4, blocks.size)
        val title = blocks[0] as MdBlock.Heading
        assertEquals(2, title.level)
        assertEquals("1.196.0", title.spans.first().text)
        assertEquals("https://github.com/x/photonne/compare/v1.195.0...v1.196.0", title.spans.first().url)
        assertEquals(MdBlock.Heading(3, listOf(MdSpan("Features"))), blocks[1])

        val item = blocks[2] as MdBlock.ListItem
        assertEquals("•", item.marker)
        assertEquals(MdSpan("spa:", bold = true), item.spans.first())
        assertEquals(listOf("#12", "261bdcc"), item.spans.filter { it.url != null }.map { it.text })
        assertEquals(MdBlock.ListItem(0, "•", listOf(MdSpan("Otra cosa"))), blocks[3])
    }

    @Test
    fun only_web_links_are_tappable() {
        val spans = parseInline("[a](javascript:alert(1)) [b](file:///etc/passwd) [c](https://ok.example/x)")

        // The unsafe ones are plain text, label and all.
        assertEquals(listOf("https://ok.example/x"), spans.mapNotNull { it.url })
        assertTrue(spans.first().text.startsWith("a"))
        assertEquals("c", spans.single { it.url != null }.text)
        assertFalse(isSafeLink("intent://x"))
        assertFalse(isSafeLink("https://"))
        assertTrue(isSafeLink("http://example.com"))
    }

    @Test
    fun bare_urls_are_links_without_trailing_punctuation() {
        val spans = parseInline("**Full Changelog**: https://github.com/x/compare/a...b.")

        assertEquals(MdSpan("Full Changelog", bold = true), spans[0])
        assertEquals(MdSpan(": "), spans[1])
        assertEquals("https://github.com/x/compare/a...b", spans[2].url)
        assertEquals(MdSpan("."), spans[3])
    }

    @Test
    fun a_stray_bold_marker_stays_text() {
        assertEquals(listOf(MdSpan("2 ** 3 = 8")), parseInline("2 ** 3 = 8"))
    }

    @Test
    fun paragraph_lines_join_and_numbered_items_keep_their_number() {
        val blocks = parseReleaseNotes("Una línea\nque sigue\n\n1. Primero\n  - Anidado")

        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Una línea que sigue"))), blocks[0])
        assertEquals(MdBlock.ListItem(0, "1.", listOf(MdSpan("Primero"))), blocks[1])
        assertEquals(MdBlock.ListItem(1, "•", listOf(MdSpan("Anidado"))), blocks[2])
    }

    @Test
    fun inline_code_shows_its_text_and_rules_disappear() {
        val blocks = parseReleaseNotes("Usa `docker compose up`\n\n---\n\nFin")

        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Usa docker compose up"))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Fin"))), blocks[1])
    }
}
