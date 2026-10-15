package com.photonne.app.data.devicebackup

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cross-checks the Kotlin SHA-256 against the JDK for every length up to 300 bytes. */
class Sha256JvmParityTest {

    @Test
    fun matches_message_digest_for_every_small_length() {
        val random = Random(42)
        for (length in 0..300) {
            val input = random.nextBytes(length)
            val expected = MessageDigest.getInstance("SHA-256").digest(input).toLowerHex()
            val digest = Sha256()
            digest.update(input, 0, input.size)
            assertEquals(expected, digest.digest().toLowerHex(), "length=$length")
        }
    }
}
