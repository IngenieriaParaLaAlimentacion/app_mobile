package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository

/** Live-first: falls back to the local queue only when offline and it's this device's own row. */
class GetSurveyResponseDetailUseCase(private val repository: SurveyResponseHistoryRepository) {
    suspend operator fun invoke(surveyId: String, responseId: String): Result<SurveyResponseDetail> =
        repository.getDetail(surveyId, responseId)
}
