package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail

/** Page of [Survey] summaries plus paging metadata, mirrors [SubjectPage]-style shapes used elsewhere. */
data class PagedSurveys(
    val items: List<Survey>,
    val page: Int,
    val totalPages: Int,
    /** True when served from the local cache because the network failed (offline fallback). */
    val fromCache: Boolean = false,
)

interface SurveyRepository {
    suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys>

    /** Cache-first: warm CachedSurvey row if present, else fetch + cache. */
    suspend fun getDetail(surveyId: String): Result<SurveyDetail>

    /** Best-effort full-snapshot warm: fetches every /available page (capped), replaces the local
     *  cache (drift fix, detailJson preserved), then prefetches details for surveys missing them.
     *  Never throws; partial progress is kept. */
    suspend fun warmAvailableSurveys()
}
