package ar.com.ingenieriaalimentacion.data.db

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner
import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleanupException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightDatabaseCleaner(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DatabaseCleaner {
    override suspend fun clearCaches() = clean { clearCacheTables() }

    override suspend fun clearForSignOut(ownerId: String) = clean {
        clearCacheTables()
        database.pendingSubjectQueries.deleteForOwner(ownerId)
        database.pendingMeasurementQueries.deleteForOwner(ownerId)
        database.pendingSurveyResponseQueries.deleteForOwner(ownerId)
        database.pendingSurveyResponseEditQueries.deleteForOwner(ownerId)
    }

    private suspend fun clean(write: () -> Unit) = withContext(dispatcher) {
        try {
            database.transaction { write() }
        } catch (failure: Exception) {
            if (failure.isSqliteFailure()) throw DatabaseCleanupException(failure)
            throw failure
        }
    }

    private fun clearCacheTables() {
        database.cachedEstablishmentQueries.deleteAll()
        database.cachedActivityItemQueries.deleteAll()
        database.currentEstablishmentQueries.deleteAll()
        database.cachedSurveyQueries.deleteAll()
    }
}
