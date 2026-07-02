package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryPage
import kotlinx.coroutines.flow.Flow

interface MeasurementHistoryRepository {
    /** Reactive local-only merge (queue ∪ cache), no network — used by Home and as History's floor. */
    fun observeLocalMerged(): Flow<List<MeasurementHistoryEntry>>

    /** Network fetch, cache upsert, then merge per ADR-009 (local wins on collision). */
    suspend fun loadPage(page: Int, pageSize: Int): Result<MeasurementHistoryPage>
}
