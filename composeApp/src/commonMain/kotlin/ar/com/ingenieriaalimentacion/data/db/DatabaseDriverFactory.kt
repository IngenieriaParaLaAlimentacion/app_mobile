package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform [SqlDriver] for [AppDatabase]. Mirrors the existing
 * `expect/actual` persistence precedent (`TokenStore`, `DeviceIdStore`).
 */
interface DatabaseDriverFactory {
    fun create(): SqlDriver
}
