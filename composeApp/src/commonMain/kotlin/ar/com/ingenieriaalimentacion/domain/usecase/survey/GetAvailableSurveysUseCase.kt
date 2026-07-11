package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

class GetAvailableSurveysUseCase(private val repository: SurveyRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int): Result<PagedSurveys> =
        repository.getAvailable(page, pageSize)
}
