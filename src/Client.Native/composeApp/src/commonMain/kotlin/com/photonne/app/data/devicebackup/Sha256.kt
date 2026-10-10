package com.photonne.app.data.devicebackup

/**
 * Minimal incremental SHA-256 used by the iOS device-sync flow, where
 * we don't have a cheap path to Apple's CommonCrypto from Kotlin/Native
 * without a custom cinterop. Android and desktop bypass this and use
 * [java.security.MessageDigest] directly, which is roughly 10× faster.
 *
 * Algorithm follows FIPS 180-4 §6.2. Not constant-time — only used
 * for content addressing (not for HMAC / signatures).
 */
internal class Sha256 {

    private val h = INITIAL_HASH.copyOf()
    private val buffer = ByteArray(BLOCK_SIZE)
    private var bufferLen = 0
    private var totalLen = 0L

    fun update(data: ByteArray, offset: Int, len: Int) {
        if (len <= 0) return
        totalLen += len
        var srcOff = offset
        var remaining = len

        // Fill any partial block left over from the previous update.
        if (bufferLen > 0) {
            val toCopy = minOf(BLOCK_SIZE - bufferLen, remaining)
            data.copyInto(buffer, bufferLen, srcOff, srcOff + toCopy)
            bufferLen += toCopy
            srcOff += toCopy
            remaining -= toCopy
            if (bufferLen == BLOCK_SIZE) {
                processBlock(buffer, 0)
                bufferLen = 0
            }
        }

        // Process full blocks straight out of the input buffer.
        while (remaining >= BLOCK_SIZE) {
            processBlock(data, srcOff)
            srcOff += BLOCK_SIZE
            remaining -= BLOCK_SIZE
        }

        // Hold the tail for the next call.
        if (remaining > 0) {
            data.copyInto(buffer, 0, srcOff, srcOff + remaining)
            bufferLen = remaining
        }
    }

    fun digest(): ByteArray {
        val bitLen = totalLen * 8L
        // Append 0x80 then pad with zeros until 8 bytes shy of a block,
        // then write the original message length as a big-endian u64.
        buffer[bufferLen++] = 0x80.toByte()
        if (bufferLen > BLOCK_SIZE - 8) {
            while (bufferLen < BLOCK_SIZE) buffer[bufferLen++] = 0
            processBlock(buffer, 0)
            bufferLen = 0
        }
        while (bufferLen < BLOCK_SIZE - 8) buffer[bufferLen++] = 0
        for (i in 7 downTo 0) {
            buffer[bufferLen++] = ((bitLen ushr (i * 8)) and 0xff).toByte()
        }
        processBlock(buffer, 0)

        val out = ByteArray(32)
        for (i in 0 until 8) {
            val v = h[i]
            out[i * 4] = (v ushr 24 and 0xff).toByte()
            out[i * 4 + 1] = (v ushr 16 and 0xff).toByte()
            out[i * 4 + 2] = (v ushr 8 and 0xff).toByte()
            out[i * 4 + 3] = (v and 0xff).toByte()
        }
        return out
    }

    private fun processBlock(input: ByteArray, offset: Int) {
        val w = IntArray(64)
        for (i in 0 until 16) {
            val o = offset + i * 4
            w[i] = ((input[o].toInt() and 0xff) shl 24) or
                ((input[o + 1].toInt() and 0xff) shl 16) or
                ((input[o + 2].toInt() and 0xff) shl 8) or
                (input[o + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotr(7) xor w[i - 15].rotr(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotr(17) xor w[i - 2].rotr(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

        for (i in 0 until 64) {
            val s1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[i] + w[i]
            val s0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    private companion object {
        const val BLOCK_SIZE = 64

        val INITIAL_HASH = intArrayOf(
            0x6a09e667.toInt(), 0xbb67ae85.toInt(),
            0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
            0x510e527f.toInt(), 0x9b05688c.toInt(),
            0x1f83d9ab.toInt(), 0x5be0cd19.toInt()
        )

        val K = intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
            0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
            0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
            0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
            0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
            0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
            0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
            0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
        )
    }
}

private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))

/** Lowercase hex representation of a 32-byte digest. */
internal fun ByteArray.toLowerHex(): String {
    val sb = StringBuilder(size * 2)
    val table = "0123456789abcdef"
    for (b in this) {
        val v = b.toInt() and 0xff
        sb.append(table[v ushr 4])
        sb.append(table[v and 0x0f])
    }
    return sb.toString()
}
