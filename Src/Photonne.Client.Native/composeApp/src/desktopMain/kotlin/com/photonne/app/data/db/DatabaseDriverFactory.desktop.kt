package com.photonne.app.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.photonne.app.db.PhotonneDatabase
import java.io.File

actual fun createPhotonneDatabaseDriver(): SqlDriver {
    val dir = File(System.getProperty("user.home"), ".photonne")
    dir.mkdirs()
    val file = File(dir, "photonne.db")
    val isNew = !file.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    if (isNew) {
        PhotonneDatabase.Schema.create(driver)
    }
    return driver
}
