package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository

class GetSurveyResponseHistoryPageUseCase(private val repository: SurveyResponseHistoryRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> =
        repository.getPage(page, pageSize)
}
