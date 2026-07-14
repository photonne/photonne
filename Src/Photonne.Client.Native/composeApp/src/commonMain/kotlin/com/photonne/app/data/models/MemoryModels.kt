package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A card in the Recuerdos feed, generated nightly by the server.
 *
 * [title] and [subtitle] arrive already rendered: the wording lives on the
 * server so the three clients can't drift from each other. Do NOT rebuild a
 * label from [kind] — it exists to group and to pick an icon, not to write copy.
 */
@Serializable
data class Memory(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val coverAssetId: String? = null,
    val assetCount: Int = 0,
    // Capture-date span in the photo's own wall-clock, like TimelineItem.fileCreatedAt.
    // Decode with captureLocalDate(), never the device zone.
    @Serializable(with = FlexibleInstantSerializer::class) val windowStart: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val windowEnd: Instant,
)

/** A memory plus its assets, in display order — index 0 is the cover. */
@Serializable
data class MemoryDetail(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val coverAssetId: String? = null,
    val assetCount: Int = 0,
    @Serializable(with = FlexibleInstantSerializer::class) val windowStart: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val windowEnd: Instant,
    val assets: List<TimelineItem> = emptyList(),
)

/**
 * The server's MemoryKind, mirrored. [Unknown] is the whole point of this enum
 * existing: a newer server will send kinds this build has never heard of, and
 * the feed must render them as plain cards rather than crash.
 */
enum class MemoryKind(val wire: String) {
    OnThisDay("OnThisDay"),
    ThisMonth("ThisMonth"),
    PersonThroughYears("PersonThroughYears"),
    PeopleTogether("PeopleTogether"),
    FavoritesOfYear("FavoritesOfYear"),
    CuratedScene("CuratedScene"),
    PetsAndFood("PetsAndFood"),
    Trip("Trip"),
    Unknown("");

    companion object {
        fun from(wire: String): MemoryKind =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: Unknown
    }
}
