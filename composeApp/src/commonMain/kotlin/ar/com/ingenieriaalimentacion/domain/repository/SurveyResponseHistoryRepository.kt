package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary

interface SurveyResponseHistoryRepository {
    /** Remote list (unioned with local pending in the impl), page 1 only needs the union. */
    suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>>

    /** Always prefers a live fetch; falls back to a local copy only when offline AND it's
     *  this device's own not-yet-pruned submission. */
    suspend fun getDetail(responseId: String): Result<SurveyResponseDetail>
}
