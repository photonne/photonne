package com.photonne.app.data.devicebackup

import com.photonne.app.db.PhotonneDatabase
import com.photonne.app.db.BackupLedger as BackupLedgerRow
import kotlinx.datetime.Clock

/** Persisted verdict for one ledger row. Mirrors [DeviceMediaSyncState]
 *  minus the transient `Uploading` state, which never survives a restart. */
enum class LedgerState {
    Unknown, NotSynced, Synced, Failed, Ignored;

    companion object {
        fun fromDb(value: String): LedgerState = when (value) {
            "NOT_SYNCED" -> NotSynced
            "SYNCED" -> Synced
            "FAILED" -> Failed
            "IGNORED" -> Ignored
            else -> Unknown
        }
    }

    fun toDb(): String = when (this) {
        Unknown -> "UNKNOWN"
        NotSynced -> "NOT_SYNCED"
        Synced -> "SYNCED"
        Failed -> "FAILED"
        Ignored -> "IGNORED"
    }
}

/** One file's persisted sync bookkeeping. The (sizeBytes, dateModifiedMillis)
 *  pair is the fingerprint that decides whether [sha256] is still valid. */
data class LedgerEntry(
    val uri: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val sha256: String?,
    val state: LedgerState,
    val assetId: String?,
    val failureReason: String?,
    val failureDetail: String?,
    val lastVerifiedAtMillis: Long?
) {
    fun matchesFingerprint(media: DeviceMedia): Boolean =
        sizeBytes == media.sizeBytes && dateModifiedMillis == media.dateModifiedMillis

    fun toSyncState(): DeviceMediaSyncState = when (state) {
        LedgerState.Unknown -> DeviceMediaSyncState.Unknown
        LedgerState.NotSynced -> DeviceMediaSyncState.NotSynced
        LedgerState.Synced -> DeviceMediaSyncState.Synced(assetId.orEmpty())
        LedgerState.Failed -> DeviceMediaSyncState.Failed(
            reason = failureReason
                ?.let { raw -> UploadFailureReason.entries.firstOrNull { it.name == raw } }
                ?: UploadFailureReason.Unknown,
            detail = failureDetail
        )
        LedgerState.Ignored -> DeviceMediaSyncState.Ignored
    }
}

/**
 * Persistent per-file sync ledger backing the device backup flow.
 *
 * The contract: hashing is expensive (full file read), so a SHA-256 is
 * computed at most once per (file, fingerprint) and stored here. Server
 * verdicts are persisted too, so revisiting the Backup screen costs one
 * bulk HTTP call instead of a full re-hash + per-file lookup.
 *
 * Verdicts are only meaningful against one account on one server: callers
 * must invoke [ensureScope] with the current "serverUrl|username" pair —
 * a mismatch wipes the ledger so stale "synced" flags never leak across
 * accounts (the same guarantee the old in-memory model gave for free).
 */
class BackupLedger(private val database: PhotonneDatabase) {

    private val queries get() = database.backupLedgerQueries

    // ─── Account scope ───────────────────────────────────────────────────────

    fun ensureScope(scope: String) {
        val current = queries.selectMeta(META_SCOPE).executeAsOneOrNull()
        if (current == scope) return
        queries.transaction {
            queries.clearAll()
            queries.upsertMeta(META_SCOPE, scope)
        }
    }

    fun clear() {
        queries.transaction {
            queries.clearAll()
            queries.clearMeta()
        }
    }

    // ─── Reads ───────────────────────────────────────────────────────────────

    fun entries(folderUri: String): Map<String, LedgerEntry> =
        queries.selectByFolder(folderUri).executeAsList()
            .associate { it.uri to it.toEntry() }

    fun countsByState(folderUri: String): Map<LedgerState, Long> =
        queries.countByState(folderUri).executeAsList()
            .associate { LedgerState.fromDb(it.syncState) to it.total }

    // ─── Reconciliation ──────────────────────────────────────────────────────

    /**
     * Aligns the ledger with a fresh folder scan:
     * - new files get an UNKNOWN row,
     * - files whose fingerprint changed are reset (stale hash dropped),
     * - rows whose file disappeared are deleted,
     * - untouched rows keep their hash + verdict (the whole point).
     *
     * Returns the resulting ledger as a uri-keyed map.
     */
    fun reconcile(folderUri: String, scanned: List<DeviceMedia>): Map<String, LedgerEntry> {
        val result = LinkedHashMap<String, LedgerEntry>(scanned.size)
        queries.transaction {
            val existing = queries.selectByFolder(folderUri).executeAsList()
                .associateBy { it.uri }
            val scannedUris = HashSet<String>(scanned.size)

            for (media in scanned) {
                scannedUris.add(media.uri)
                val row = existing[media.uri]?.toEntry()
                if (row != null && row.matchesFingerprint(media)) {
                    result[media.uri] = row
                    continue
                }
                // New file, or contents changed since we hashed it.
                val fresh = LedgerEntry(
                    uri = media.uri,
                    sizeBytes = media.sizeBytes,
                    dateModifiedMillis = media.dateModifiedMillis,
                    sha256 = null,
                    state = LedgerState.Unknown,
                    assetId = null,
                    failureReason = null,
                    failureDetail = null,
                    lastVerifiedAtMillis = null
                )
                insert(folderUri, fresh)
                result[media.uri] = fresh
            }

            val missing = existing.keys.filterNot { it in scannedUris }
            missing.chunked(SQLITE_IN_CHUNK).forEach { chunk ->
                queries.deleteByUris(folderUri, chunk)
            }
        }
        return result
    }

    // ─── Writes ──────────────────────────────────────────────────────────────

    fun setHash(folderUri: String, uri: String, sha256: String) {
        queries.setHash(sha256, folderUri, uri)
    }

    fun setVerdict(folderUri: String, uri: String, state: LedgerState, assetId: String?) {
        queries.setVerdict(
            syncState = state.toDb(),
            assetId = assetId,
            lastVerifiedAtMillis = Clock.System.now().toEpochMilliseconds(),
            folderUri = folderUri,
            uri = uri
        )
    }

    /** Batch variant of [setVerdict] — one transaction for a whole bulk-check page. */
    fun setVerdicts(folderUri: String, verdicts: List<Triple<String, LedgerState, String?>>) {
        if (verdicts.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        queries.transaction {
            for ((uri, state, assetId) in verdicts) {
                queries.setVerdict(
                    syncState = state.toDb(),
                    assetId = assetId,
                    lastVerifiedAtMillis = now,
                    folderUri = folderUri,
                    uri = uri
                )
            }
        }
    }

    fun markUploaded(folderUri: String, uri: String, assetId: String) {
        setVerdict(folderUri, uri, LedgerState.Synced, assetId)
    }

    fun markUploadFailed(
        folderUri: String,
        uri: String,
        reason: UploadFailureReason,
        detail: String?
    ) {
        queries.setFailure(reason.name, detail, folderUri, uri)
    }

    /** Skip one file: it leaves the pending pipeline until [unignore]. */
    fun markIgnored(folderUri: String, uri: String) {
        queries.setIgnored(folderUri, uri)
    }

    /** Skip every currently-failed file under [folderUri] in one statement. */
    fun ignoreFailed(folderUri: String) {
        queries.setIgnoredByState(folderUri, LedgerState.Failed.toDb())
    }

    /** Put a skipped file back in the pipeline (resets it to UNKNOWN). */
    fun unignore(folderUri: String, uri: String) {
        queries.clearIgnored(folderUri, uri)
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private fun insert(folderUri: String, entry: LedgerEntry) {
        queries.insertEntry(
            folderUri = folderUri,
            uri = entry.uri,
            sizeBytes = entry.sizeBytes,
            dateModifiedMillis = entry.dateModifiedMillis,
            sha256 = entry.sha256,
            syncState = entry.state.toDb(),
            assetId = entry.assetId,
            failureReason = entry.failureReason,
            lastVerifiedAtMillis = entry.lastVerifiedAtMillis,
            failureDetail = entry.failureDetail
        )
    }

    private fun BackupLedgerRow.toEntry() = LedgerEntry(
        uri = uri,
        sizeBytes = sizeBytes,
        dateModifiedMillis = dateModifiedMillis,
        sha256 = sha256,
        state = LedgerState.fromDb(syncState),
        assetId = assetId,
        failureReason = failureReason,
        failureDetail = failureDetail,
        lastVerifiedAtMillis = lastVerifiedAtMillis
    )

    private companion object {
        const val META_SCOPE = "account_scope"

        // SQLite caps host parameters at 999 per statement; stay under it.
        const val SQLITE_IN_CHUNK = 900
    }
}
