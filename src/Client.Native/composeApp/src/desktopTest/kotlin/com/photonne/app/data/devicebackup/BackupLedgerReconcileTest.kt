package com.photonne.app.data.devicebackup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.photonne.app.db.PhotonneDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Desktop-only for the in-memory JDBC SQLite driver; the ledger logic is common. */
class BackupLedgerReconcileTest {

    private val folder = "photokit:camera-roll"

    private fun ledger(): BackupLedger {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PhotonneDatabase.Schema.create(driver)
        return BackupLedger(PhotonneDatabase(driver))
    }

    private fun media(uri: String, modified: Long) = DeviceMedia(
        uri = uri,
        displayName = "IMG_0001.HEIC",
        relativePath = "",
        mimeType = "image/heic",
        sizeBytes = 0L, // iOS: PhotoKit gives no size
        dateModifiedMillis = modified,
        type = DeviceMediaType.Image
    )

    @Test
    fun a_skip_survives_a_fingerprint_change() {
        val ledger = ledger()
        ledger.reconcile(folder, listOf(media("photokit:a", 1_000)))
        ledger.markIgnored(folder, "photokit:a")

        // A favorite in Photos bumps modificationDate.
        val entry = ledger.reconcile(folder, listOf(media("photokit:a", 2_000))).getValue("photokit:a")

        assertEquals(LedgerState.Ignored, entry.state)
        assertEquals(2_000, entry.dateModifiedMillis)
    }

    @Test
    fun a_synced_file_keeps_its_verdict_with_the_old_hash_set_aside() {
        val ledger = ledger()
        ledger.reconcile(folder, listOf(media("photokit:a", 1_000)))
        ledger.setHash(folder, "photokit:a", "hash-1")
        ledger.markUploaded(folder, "photokit:a", "asset-1")

        val entry = ledger.reconcile(folder, listOf(media("photokit:a", 2_000))).getValue("photokit:a")

        assertEquals(LedgerState.Synced, entry.state)
        assertEquals("asset-1", entry.assetId)
        assertNull(entry.sha256) // re-hashed before anything trusts it again
        assertEquals("hash-1", entry.previousSha256)
    }

    @Test
    fun setting_the_hash_clears_the_previous_one() {
        val ledger = ledger()
        ledger.reconcile(folder, listOf(media("photokit:a", 1_000)))
        ledger.setHash(folder, "photokit:a", "hash-1")
        ledger.reconcile(folder, listOf(media("photokit:a", 2_000)))

        ledger.setHash(folder, "photokit:a", "hash-1")

        val entry = ledger.entries(folder).getValue("photokit:a")
        assertEquals("hash-1", entry.sha256)
        assertNull(entry.previousSha256)
    }
}

/** An installed app's ledger (schema v4, before previousSha256) upgraded in place. */
class BackupLedgerMigrationTest {

    @Test
    fun a_v4_ledger_migrates_keeping_its_rows() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Build the current schema, then roll backupLedger back to its v4 shape.
        PhotonneDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE backupLedger", 0)
        driver.execute(
            null,
            """
            CREATE TABLE backupLedger (
                folderUri TEXT NOT NULL,
                uri TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                dateModifiedMillis INTEGER NOT NULL,
                sha256 TEXT,
                syncState TEXT NOT NULL DEFAULT 'UNKNOWN',
                assetId TEXT,
                failureReason TEXT,
                lastVerifiedAtMillis INTEGER,
                failureDetail TEXT,
                PRIMARY KEY (folderUri, uri)
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            "INSERT INTO backupLedger(folderUri, uri, sizeBytes, dateModifiedMillis, sha256, syncState, assetId) " +
                "VALUES ('f', 'photokit:a', 0, 1000, 'hash-1', 'SYNCED', 'asset-1')",
            0
        )

        PhotonneDatabase.Schema.migrate(driver, oldVersion = 4, newVersion = PhotonneDatabase.Schema.version)

        val entry = BackupLedger(PhotonneDatabase(driver)).entries("f").getValue("photokit:a")
        assertEquals(LedgerState.Synced, entry.state)
        assertEquals("hash-1", entry.sha256)
        assertNull(entry.previousSha256)
        assertEquals(5L, PhotonneDatabase.Schema.version)
    }
}
