package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

/**
 * [getAvailable] is a thin live call — availability is time-sensitive, so the cache is written but
 * not read-through. [getDetail] is cache-first: a survey opened once while online is answerable
 * fully offline afterward.
 */
class SurveyRepositoryImpl(
    private val api: SurveyApi,
    private val local: SurveyLocalStore,
) : SurveyRepository {

    override suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys> = runCatching {
        val response = api.available(page, pageSize)
        local.upsertSummaries(response.items.map { it.toDomain() })
        response.toDomain()
    }

    override suspend fun getDetail(surveyId: String): Result<SurveyDetail> {
        local.findDetail(surveyId)?.let { return Result.success(it) }
        return when (val outcome = api.detail(surveyId)) {
            is SurveyDetailOutcome.Found -> {
                val detail = outcome.response.toDomain()
                local.upsertDetail(detail)
                Result.success(detail)
            }
            SurveyDetailOutcome.Forbidden -> Result.failure(SurveyFeatureFailure(SurveyFeatureError.Forbidden, null))
            SurveyDetailOutcome.NotFound -> Result.failure(SurveyFeatureFailure(SurveyFeatureError.NotFound, null))
            is SurveyDetailOutcome.Retryable ->
                Result.failure(SurveyFeatureFailure(SurveyFeatureError.Network, outcome.cause))
        }
    }
}
