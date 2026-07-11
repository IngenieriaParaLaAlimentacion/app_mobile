package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository

class FakeSurveyResponseHistoryRepository : SurveyResponseHistoryRepository {
    var pageResult: Result<List<SurveyResponseSummary>> = Result.success(emptyList())
    var detailResult: Result<SurveyResponseDetail> = Result.failure(IllegalStateException("no detail configured"))

    override suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> = pageResult

    override suspend fun getDetail(responseId: String): Result<SurveyResponseDetail> = detailResult
}
