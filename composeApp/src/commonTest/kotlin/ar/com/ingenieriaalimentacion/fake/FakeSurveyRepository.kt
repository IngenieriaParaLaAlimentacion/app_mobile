package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

class FakeSurveyRepository : SurveyRepository {
    var availableResult: Result<PagedSurveys> = Result.success(PagedSurveys(emptyList(), 1, 1))
    var detailResult: Result<SurveyDetail> = Result.failure(IllegalStateException("no detail configured"))

    override suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys> = availableResult

    override suspend fun getDetail(surveyId: String): Result<SurveyDetail> = detailResult
}
