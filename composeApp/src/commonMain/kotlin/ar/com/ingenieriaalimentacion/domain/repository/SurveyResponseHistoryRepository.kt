package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import kotlinx.coroutines.flow.Flow

interface SurveyResponseHistoryRepository {
    /** Reactive floor: local pending queue merged with the persistent activity cache — always
     *  populated offline, updates live as sync state changes. Mirrors
     *  [SubjectHistoryRepository.observeLocalMerged]/[MeasurementHistoryRepository.observeLocalMerged]. */
    fun observeLocalMerged(): Flow<List<SurveyResponseSummary>>

    /** Remote list (unioned with local pending in the impl), page 1 only needs the union. */
    suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>>

    /** Always prefers a live fetch; falls back to a local copy only when offline AND it's
     *  this device's own not-yet-pruned submission. */
    suspend fun getDetail(responseId: String): Result<SurveyResponseDetail>
}
