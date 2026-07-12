package ar.com.ingenieriaalimentacion.data.db

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wipes every local table in a single transaction. Used on explicit sign-out. */
class SqlDelightDatabaseCleaner(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DatabaseCleaner {
    override suspend fun clearAll(): Unit = withContext(dispatcher) {
        database.transaction {
            database.pendingSubjectQueries.deleteAll()
            database.pendingMeasurementQueries.deleteAll()
            database.cachedEstablishmentQueries.deleteAll()
            database.cachedActivityItemQueries.deleteAll()
            database.currentEstablishmentQueries.deleteAll()
            database.cachedSurveyQueries.deleteAll()
            database.pendingSurveyResponseQueries.deleteAll()
            database.pendingSurveyResponseEditQueries.deleteAll()
        }
    }
}
