package com.photonne.app.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.photonne.app.db.PhotonneDatabase

actual fun createPhotonneDatabaseDriver(): SqlDriver =
    NativeSqliteDriver(PhotonneDatabase.Schema, "photonne.db")
