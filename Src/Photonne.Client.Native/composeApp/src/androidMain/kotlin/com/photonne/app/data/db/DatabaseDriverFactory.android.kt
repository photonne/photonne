package com.photonne.app.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.photonne.app.db.PhotonneDatabase
import org.koin.core.context.GlobalContext

actual fun createPhotonneDatabaseDriver(): SqlDriver {
    // Pull the Context that PhotonneApplication registered via `androidContext()`.
    val context: Context = GlobalContext.get().get()
    return AndroidSqliteDriver(PhotonneDatabase.Schema, context, "photonne.db")
}
