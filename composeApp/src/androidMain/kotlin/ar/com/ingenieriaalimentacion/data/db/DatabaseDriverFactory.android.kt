package ar.com.ingenieriaalimentacion.data.db

import android.database.sqlite.SQLiteException
import java.sql.SQLException
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, "ingenieria_alimentacion.db")
}

internal actual fun Throwable.isSqliteFailure(): Boolean = this is SQLiteException || this is SQLException
