package com.photonne.app.data.devicebackup

import com.photonne.app.db.PhotonneDatabase
import com.photonne.app.db.BackupLedger as BackupLedgerRow
import kotlin.time.Clock

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
    val lastVerifiedAtMillis: Long?,
    /** The hash before the fingerprint last changed, until the file is
     *  re-hashed (see [BackupLedger.reconcile]). */
    val previousSha256: String? = null
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
 * must invoke [ensureScope] with the current "serverUrl|userId" pair —
 * a mismatch wipes the ledger so stale "synced" flags never leak across
 * accounts (the same guarantee the old in-memory model gave for free).
 */
class BackupLedger(private val database: PhotonneDatabase) {

    private val queries get() = database.backupLedgerQueries

    // ─── Account scope ───────────────────────────────────────────────────────

    /**
     * Binds the ledger to [scope] ("serverUrl|userId") and tells the caller
     * whether the account changed. [legacyScope] is the pre-user-id key
     * ("serverUrl|username") of the same account: a ledger still tagged with
     * it is adopted as-is instead of wiped, so upgrading never rehashes the
     * whole gallery nor loses the user's "Omitir" choices.
     */
    fun ensureScope(scope: String, legacyScope: String? = null): ScopeChange {
        val current = queries.selectMeta(META_SCOPE).executeAsOneOrNull()
        if (current == scope) return ScopeChange.Same
        if (current != null && current == legacyScope) {
            queries.upsertMeta(META_SCOPE, scope)
            return ScopeChange.Same
        }
        queries.transaction {
            queries.clearAll()
            // The device↔server identity map is scoped to the same account:
            // its assetIds belong to one server/user pair just like the
            // ledger's verdicts, so both wipe atomically together.
            database.deviceIdentityQueries.clearIdentities()
            queries.upsertMeta(META_SCOPE, scope)
        }
        return if (current == null) ScopeChange.Fresh else ScopeChange.Switched
    }

    /** What [ensureScope] found about the previous account on this install. */
    enum class ScopeChange {
        /** Same account as last time (possibly re-keyed from its legacy scope). */
        Same,
        /** A different account than the last one that signed in here. */
        Switched,
        /** No account had signed in before (fresh install or wiped ledger). */
        Fresh
    }

    fun clear() {
        queries.transaction {
            queries.clearAll()
            database.deviceIdentityQueries.clearIdentities()
            queries.clearMeta()
        }
    }

    /** Forgets everything known about one folder (it stopped being backed up). */
    fun clearFolder(folderUri: String) {
        queries.clearFolder(folderUri)
    }

    // ─── Side-channel storage ────────────────────────────────────────────────
    // Small key/value payloads that need to survive a process hop but are too
    // big for WorkManager's ~10 KB input Data — chiefly the list of URIs a
    // "sync these" request hands to the foreground worker.

    fun putMeta(key: String, value: String) {
        queries.upsertMeta(key, value)
    }

    fun meta(key: String): String? = queries.selectMeta(key).executeAsOneOrNull()

    /** Reads and removes in one go, so a payload is consumed exactly once. */
    fun takeMeta(key: String): String? {
        var value: String? = null
        queries.transaction {
            value = queries.selectMeta(key).executeAsOneOrNull()
            queries.deleteMeta(key)
        }
        return value
    }

    // ─── Reads ───────────────────────────────────────────────────────────────

    fun entries(folderUri: String): Map<String, LedgerEntry> =
        queries.selectByFolder(folderUri).executeAsList()
            .associate { it.uri to it.toEntry() }

    /** Every row across all folders — the device-library overlay joins these
     *  against the MediaStore/PhotoKit enumeration by uri and fingerprint. */
    fun allEntries(): List<LedgerEntry> =
        queries.selectAll().executeAsList().map { it.toEntry() }

    fun countsByState(folderUri: String): Map<LedgerState, Long> =
        queries.countByState(folderUri).executeAsList()
            .associate { LedgerState.fromDb(it.syncState) to it.total }

    // ─── Reconciliation ──────────────────────────────────────────────────────

    /**
     * Aligns the ledger with a fresh folder scan:
     * - new files get an UNKNOWN row,
     * - files whose fingerprint changed are queued for a re-hash (stale hash
 *   set aside); a skip stays, a SYNCED verdict stays until the new hash
 *   says the content changed,
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
                // The fingerprint moved, which doesn't always mean the file
                // did: on iOS it is only PhotoKit's modificationDate, bumped
                // by a favorite or a hide. A skip is the user's call about
                // this photo, so it stays whatever happened to it.
                if (row != null && row.state == LedgerState.Ignored) {
                    // Its hash is set aside, not kept: under the new
                    // fingerprint it would pass for current, and after an
                    // unignore get checked against the server (or seeded
                    // into the identity map) for content that may be gone.
                    val moved = row.copy(
                        sizeBytes = media.sizeBytes,
                        dateModifiedMillis = media.dateModifiedMillis,
                        sha256 = null,
                        previousSha256 = row.sha256 ?: row.previousSha256
                    )
                    insert(folderUri, moved)
                    result[media.uri] = moved
                    continue
                }
                // New file, or possibly changed since we hashed it: re-hash.
                // A backed-up file keeps its verdict meanwhile, with the old
                // hash aside: if the new one matches, nothing changed and the
                // verdict stands; if not, it's reset then (see
                // DeviceBackupRepository.applyHash). Without a hash it can't
                // be freed or seeded in the meantime.
                // Only with a hash to compare against: a file uploaded straight
                // from a selection was never hashed, and kept Synced it could
                // never tell an edit apart, so the edited version would never
                // go up.
                val keepsVerdict = row?.state == LedgerState.Synced &&
                    (row.sha256 ?: row.previousSha256) != null
                val fresh = LedgerEntry(
                    uri = media.uri,
                    sizeBytes = media.sizeBytes,
                    dateModifiedMillis = media.dateModifiedMillis,
                    sha256 = null,
                    state = if (keepsVerdict) LedgerState.Synced else LedgerState.Unknown,
                    assetId = row?.assetId?.takeIf { keepsVerdict },
                    failureReason = null,
                    failureDetail = null,
                    lastVerifiedAtMillis = row?.lastVerifiedAtMillis?.takeIf { keepsVerdict },
                    previousSha256 = row?.sha256 ?: row?.previousSha256
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

    /** A failed file is fine again (it read this time): back to UNKNOWN so the
     *  next server check gives it a real verdict. Same reset as [unignore]. */
    fun clearFailure(folderUri: String, uri: String) {
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
            failureDetail = entry.failureDetail,
            previousSha256 = entry.previousSha256
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
        lastVerifiedAtMillis = lastVerifiedAtMillis,
        previousSha256 = previousSha256
    )

    private companion object {
        const val META_SCOPE = "account_scope"

        // SQLite caps host parameters at 999 per statement; stay under it.
        const val SQLITE_IN_CHUNK = 900
    }
}
