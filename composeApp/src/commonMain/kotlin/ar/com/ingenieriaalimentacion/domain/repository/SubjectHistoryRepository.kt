package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import kotlinx.coroutines.flow.Flow

interface SubjectHistoryRepository {
    /** Reactive local-only merge (queue ∪ cache), no network — used by Home and as History's floor. */
    fun observeLocalMerged(): Flow<List<SubjectHistoryEntry>>

    /** Network fetch, cache upsert, then merge per ADR-009 (local wins on collision). */
    suspend fun loadPage(page: Int, pageSize: Int): Result<SubjectHistoryPage>
}
