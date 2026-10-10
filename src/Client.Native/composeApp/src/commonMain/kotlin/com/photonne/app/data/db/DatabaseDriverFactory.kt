package com.photonne.app.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform [SqlDriver] backing [com.photonne.app.db.PhotonneDatabase].
 *
 * - Android: AndroidSqliteDriver over the app's private database dir.
 * - iOS: NativeSqliteDriver (SQLiter) in the app sandbox.
 * - Desktop: JDBC/xerial SQLite under ~/.photonne/.
 *
 * The schema is created/migrated by the driver on first open.
 */
expect fun createPhotonneDatabaseDriver(): SqlDriver
