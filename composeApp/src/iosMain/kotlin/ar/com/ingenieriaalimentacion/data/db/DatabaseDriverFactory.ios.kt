package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(AppDatabase.Schema, "ingenieria_alimentacion.db")
}
