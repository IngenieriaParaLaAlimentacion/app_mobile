package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner

class FakeDatabaseCleaner : DatabaseCleaner {
    var clearAllCount: Int = 0

    override suspend fun clearAll() {
        clearAllCount++
    }
}
