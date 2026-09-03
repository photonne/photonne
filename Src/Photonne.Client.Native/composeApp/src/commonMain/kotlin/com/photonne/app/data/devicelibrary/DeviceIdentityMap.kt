package com.photonne.app.data.devicelibrary

import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.db.DeviceIdentity as DeviceIdentityRow
import com.photonne.app.db.PhotonneDatabase

/**
 * One established (or attempted) device↔server identity. See
 * deviceIdentity.sq for the field semantics.
 */
data class DeviceIdentity(
    val uri: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val sha256: String?,
    val assetId: String?,
    val checkedAtMillis: Long?
) {
    /**
     * True while the row still describes the on-disk file. iOS never
     * exposes sizes cheaply (both sides stay 0), so the modification
     * date carries the invalidation alone there.
     */
    fun matchesFingerprint(media: DeviceMedia): Boolean =
        sizeBytes == media.sizeBytes && dateModifiedMillis == media.dateModifiedMillis
}

/**
 * Persistence for the device↔server identity map. Writes happen from
 * two producers: the ledger bridge (knowledge the backup flow already
 * paid for) and the viewport-driven resolver in [DeviceLibraryStore].
 * Reads are a single full-table load — the map is at most one row per
 * device photo, and the store keeps it in memory between changes.
 *
 * Account scoping rides on [com.photonne.app.data.devicebackup.BackupLedger.ensureScope],
 * which clears this table in the same transaction that wipes the ledger.
 */
class DeviceIdentityMap(private val database: PhotonneDatabase) {

    private val queries get() = database.deviceIdentityQueries

    fun all(): Map<String, DeviceIdentity> =
        queries.selectAllIdentities().executeAsList().associate { it.uri to it.toEntry() }

    fun upsertAll(identities: Collection<DeviceIdentity>) {
        if (identities.isEmpty()) return
        queries.transaction {
            for (identity in identities) {
                queries.upsertIdentity(
                    uri = identity.uri,
                    sizeBytes = identity.sizeBytes,
                    dateModifiedMillis = identity.dateModifiedMillis,
                    sha256 = identity.sha256,
                    assetId = identity.assetId,
                    checkedAtMillis = identity.checkedAtMillis
                )
            }
        }
    }

    fun deleteByUris(uris: Collection<String>) {
        if (uris.isEmpty()) return
        queries.transaction {
            uris.chunked(SQLITE_IN_CHUNK).forEach { chunk ->
                queries.deleteIdentitiesByUris(chunk)
            }
        }
    }

    private fun DeviceIdentityRow.toEntry() = DeviceIdentity(
        uri = uri,
        sizeBytes = sizeBytes,
        dateModifiedMillis = dateModifiedMillis,
        sha256 = sha256,
        assetId = assetId,
        checkedAtMillis = checkedAtMillis
    )

    private companion object {
        // SQLite caps host parameters at 999 per statement; stay under it.
        const val SQLITE_IN_CHUNK = 900
    }
}
