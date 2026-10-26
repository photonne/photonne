package com.photonne.app.ui.grid

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A decoded ThumbHash: [w]×[h] pixels, RGBA row by row, not premultiplied. */
class ThumbHashImage(val w: Int, val h: Int, val rgba: ByteArray)

/**
 * ThumbHash decoder (https://evanw.github.io/thumbhash/, MIT): turns the
 * ~25 bytes the server stores with each thumbnail into a 32 px sketch that
 * the grid paints, blurred, until the thumbnail arrives. A port of the
 * reference thumbHashToRGBA.
 */
object ThumbHashDecoder {
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64(hash: String?): ThumbHashImage? {
        if (hash.isNullOrEmpty()) return null
        return runCatching { decode(Base64.decode(hash)) }.getOrNull()
    }

    fun decode(bytes: ByteArray): ThumbHashImage {
        val hash = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
        val header24 = hash[0] or (hash[1] shl 8) or (hash[2] shl 16)
        val header16 = hash[3] or (hash[4] shl 8)
        val lDc = (header24 and 63) / 63.0
        val pDc = ((header24 shr 6) and 63) / 31.5 - 1
        val qDc = ((header24 shr 12) and 63) / 31.5 - 1
        val lScale = ((header24 shr 18) and 31) / 31.0
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63) / 63.0
        val qScale = ((header16 shr 9) and 63) / 63.0
        val isLandscape = (header16 shr 15) != 0
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else header16 and 7)
        val ly = max(3, if (isLandscape) header16 and 7 else (if (hasAlpha) 5 else 7))
        val aDc = if (hasAlpha) (hash[5] and 15) / 15.0 else 1.0
        val aScale = if (hasAlpha) (hash[5] shr 4) / 15.0 else 0.0
        val acStart = if (hasAlpha) 6 else 5
        var acIndex = 0

        fun decodeChannel(nx: Int, ny: Int, scale: Double): DoubleArray {
            val ac = ArrayList<Double>()
            for (cy in 0 until ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    val nibble = (hash[acStart + (acIndex shr 1)] shr ((acIndex and 1) shl 2)) and 15
                    ac.add((nibble / 7.5 - 1) * scale)
                    acIndex++
                    cx++
                }
            }
            return ac.toDoubleArray()
        }

        val lAc = decodeChannel(lx, ly, lScale)
        val pAc = decodeChannel(3, 3, pScale * 1.25)
        val qAc = decodeChannel(3, 3, qScale * 1.25)
        val aAc = if (hasAlpha) decodeChannel(5, 5, aScale) else DoubleArray(0)

        val ratio = approximateAspectRatio(hash)
        val w = jsRound(if (ratio > 1) 32.0 else 32 * ratio)
        val h = jsRound(if (ratio > 1) 32 / ratio else 32.0)
        val rgba = ByteArray(w * h * 4)
        val fx = DoubleArray(max(lx, if (hasAlpha) 5 else 3))
        val fy = DoubleArray(max(ly, if (hasAlpha) 5 else 3))
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc
                for (cx in fx.indices) fx[cx] = cos(PI / w * (x + 0.5) * cx)
                for (cy in fy.indices) fy[cy] = cos(PI / h * (y + 0.5) * cy)

                var j = 0
                for (cy in 0 until ly) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j] * fx[cx] * fy2
                        cx++
                        j++
                    }
                }
                j = 0
                for (cy in 0 until 3) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        cx++
                        j++
                    }
                }
                if (hasAlpha) {
                    j = 0
                    for (cy in 0 until 5) {
                        var cx = if (cy > 0) 0 else 1
                        val fy2 = fy[cy] * 2
                        while (cx < 5 - cy) {
                            a += aAc[j] * fx[cx] * fy2
                            cx++
                            j++
                        }
                    }
                }

                val b = l - 2.0 / 3.0 * p
                val r = (3 * l - b + q) / 2
                val g = r - q
                rgba[i] = channel(r)
                rgba[i + 1] = channel(g)
                rgba[i + 2] = channel(b)
                rgba[i + 3] = channel(a)
                i += 4
            }
        }
        return ThumbHashImage(w, h, rgba)
    }

    private fun approximateAspectRatio(hash: IntArray): Double {
        val header = hash[3]
        val hasAlpha = (hash[2] and 0x80) != 0
        val isLandscape = (hash[4] and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header and 7
        val ly = if (isLandscape) header and 7 else (if (hasAlpha) 5 else 7)
        return lx.toDouble() / ly
    }

    /** max(0, 255·min(1, v)) truncated, as a Uint8Array store does in JS. */
    private fun channel(v: Double): Byte = max(0.0, 255 * min(1.0, v)).toInt().toByte()

    /** JavaScript's Math.round: halves go up. */
    private fun jsRound(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()
}
