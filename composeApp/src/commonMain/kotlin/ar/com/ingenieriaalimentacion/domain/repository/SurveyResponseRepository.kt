package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import kotlinx.coroutines.flow.Flow

interface SurveyResponseRepository : SyncEngine {
    /** Persist a new survey response locally as PENDING. Returns immediately (offline-first). */
    suspend fun enqueue(newResponse: NewSurveyResponse): Result<Unit>

    /** Observe all locally-tracked uploads. */
    fun observeUploads(): Flow<List<SurveyResponseUpload>>

    /** Re-queue a single FAILED row for the next sync pass. */
    suspend fun retry(localId: String): Result<Unit>

    suspend fun updateResponseNumber(localId: String, responseNumber: Int): Result<Unit>

    suspend fun findQueued(localId: String): SurveyResponseUpload?

    /** Throw a queued row away for good — the *Descartar* action. The only escape for a
     *  conflicted row (which can never sync) or one dead-lettered on a terminal error. */
    suspend fun delete(localId: String): Result<Unit>

    // syncPending / resetStuckSyncing / pruneTerminal inherited from SyncEngine
}
