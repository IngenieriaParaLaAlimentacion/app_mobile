package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner

class FakeDatabaseCleaner : DatabaseCleaner {
    var clearCachesCount = 0
        private set
    val clearedOwnerIds = mutableListOf<String>()

    override suspend fun clearCaches() {
        clearCachesCount++
    }

    override suspend fun clearForSignOut(ownerId: String) {
        clearCaches()
        clearedOwnerIds += ownerId
    }
}
