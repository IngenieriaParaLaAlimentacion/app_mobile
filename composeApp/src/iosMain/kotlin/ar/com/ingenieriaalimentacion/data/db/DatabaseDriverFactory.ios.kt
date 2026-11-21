package ar.com.ingenieriaalimentacion.data.db

import co.touchlab.sqliter.interop.SQLiteException
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(AppDatabase.Schema, "ingenieria_alimentacion.db")
}

internal actual fun Throwable.isSqliteFailure(): Boolean = this is SQLiteException
