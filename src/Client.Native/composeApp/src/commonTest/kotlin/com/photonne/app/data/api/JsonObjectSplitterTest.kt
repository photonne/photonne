package com.photonne.app.data.api

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonObjectSplitterTest {

    private fun JsonObjectSplitter.feed(text: String) = text.encodeToByteArray().let { push(it, it.size) }

    @Test
    fun splits_a_json_array_written_element_by_element() {
        val splitter = JsonObjectSplitter()

        val objects = splitter.feed("""[{"a":1},{"b":{"c":[1,2]}}]""")

        assertEquals(listOf("""{"a":1}""", """{"b":{"c":[1,2]}}"""), objects)
    }

    @Test
    fun splits_ndjson() {
        val splitter = JsonObjectSplitter()

        val objects = splitter.feed("{\"a\":1}\n{\"b\":2}\n")

        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), objects)
    }

    @Test
    fun ignores_braces_and_escaped_quotes_inside_strings() {
        val splitter = JsonObjectSplitter()

        val objects = splitter.feed("""[{"message":"Copiando {x} \"y\" ]"}]""")

        assertEquals(listOf("""{"message":"Copiando {x} \"y\" ]"}"""), objects)
    }

    @Test
    fun keeps_an_object_split_across_chunks_including_multibyte_text() {
        val splitter = JsonObjectSplitter()
        val bytes = """[{"message":"Indexando año"}]""".encodeToByteArray()
        // Cut inside the two-byte "ñ".
        val cut = bytes.indexOfFirst { it == 0xC3.toByte() } + 1

        val first = splitter.push(bytes.copyOfRange(0, cut), cut)
        val second = splitter.push(bytes.copyOfRange(cut, bytes.size), bytes.size - cut)

        assertEquals(emptyList(), first)
        assertEquals(listOf("""{"message":"Indexando año"}"""), second)
    }
}
