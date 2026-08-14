package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSurveyResponseHistoryRepository : SurveyResponseHistoryRepository {
    var pageResult: Result<List<SurveyResponseSummary>> = Result.success(emptyList())
    var detailResult: Result<SurveyResponseDetail> = Result.failure(IllegalStateException("no detail configured"))
    val localMerged = MutableStateFlow<List<SurveyResponseSummary>>(emptyList())

    override fun observeLocalMerged(): Flow<List<SurveyResponseSummary>> = localMerged

    override suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> = pageResult

    override suspend fun getDetail(surveyId: String, responseId: String): Result<SurveyResponseDetail> =
        detailResult
}
