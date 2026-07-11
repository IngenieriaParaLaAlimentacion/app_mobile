package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail

/** Page of [Survey] summaries plus paging metadata, mirrors [SubjectPage]-style shapes used elsewhere. */
data class PagedSurveys(
    val items: List<Survey>,
    val page: Int,
    val totalPages: Int,
)

interface SurveyRepository {
    suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys>

    /** Cache-first: warm CachedSurvey row if present, else fetch + cache. */
    suspend fun getDetail(surveyId: String): Result<SurveyDetail>
}
