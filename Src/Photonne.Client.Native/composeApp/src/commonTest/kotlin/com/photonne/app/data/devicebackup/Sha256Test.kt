package com.photonne.app.data.devicebackup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-answer tests for the pure-Kotlin SHA-256 iOS uses to deduplicate
 * uploads: a wrong digest would re-upload files or never mark them synced.
 * Vectors from FIPS 180-2 / NIST CSRC examples.
 */
class Sha256Test {

    private fun sha256(input: ByteArray, chunk: Int = input.size.coerceAtLeast(1)): String {
        val digest = Sha256()
        var offset = 0
        while (offset < input.size) {
            val len = minOf(chunk, input.size - offset)
            digest.update(input, offset, len)
            offset += len
        }
        return digest.digest().toLowerHex()
    }

    @Test
    fun empty_input() = assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        sha256(ByteArray(0))
    )

    @Test
    fun abc() = assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        sha256("abc".encodeToByteArray())
    )

    @Test
    fun two_block_message_448_bits() = assertEquals(
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())
    )

    @Test
    fun message_896_bits() = assertEquals(
        "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
        sha256(
            ("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno" +
                "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu").encodeToByteArray()
        )
    )

    @Test
    fun one_million_a_in_uneven_chunks() = assertEquals(
        "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
        sha256(ByteArray(1_000_000) { 'a'.code.toByte() }, chunk = 997)
    )

    @Test
    fun chunking_never_changes_the_digest() {
        // Lengths around the padding edges (55/56/64 bytes) and block multiples.
        for (length in listOf(55, 56, 57, 63, 64, 65, 119, 120, 128, 1000)) {
            val input = ByteArray(length) { (it * 31 + 7).toByte() }
            val whole = sha256(input)
            for (chunk in listOf(1, 3, 63, 64, 65)) {
                assertEquals(whole, sha256(input, chunk), "length=$length chunk=$chunk")
            }
        }
    }
}
