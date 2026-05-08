package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The Photonne API serializes DateTime values as ISO-8601 strings — usually
 * with a trailing "Z" (when produced by DateTime.UtcNow) but sometimes
 * without an offset (when stored in PostgreSQL `timestamp without time
 * zone`). This serializer tolerates both forms by appending "Z" when no
 * offset is present.
 */
internal object FlexibleInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString().trim()
        val normalized = when {
            raw.endsWith("Z", ignoreCase = true) -> raw
            HAS_OFFSET_REGEX.containsMatchIn(raw) -> raw
            else -> "${raw}Z"
        }
        return Instant.parse(normalized)
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    private val HAS_OFFSET_REGEX = Regex("[+-]\\d{2}:?\\d{2}$")
}
