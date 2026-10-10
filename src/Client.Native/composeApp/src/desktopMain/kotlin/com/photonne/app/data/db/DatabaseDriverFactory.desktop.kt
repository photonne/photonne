package com.photonne.app.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.photonne.app.db.PhotonneDatabase
import java.io.File
import java.util.Properties

actual fun createPhotonneDatabaseDriver(): SqlDriver {
    val dir = File(System.getProperty("user.home"), ".photonne")
    dir.mkdirs()
    val file = File(dir, "photonne.db")
    // The schema-aware constructor tracks PRAGMA user_version and runs
    // create/migrate automatically, so desktop picks up .sqm migrations
    // the same way Android/iOS drivers do.
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        properties = Properties(),
        schema = PhotonneDatabase.Schema
    )
}
