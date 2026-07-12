package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository

/** Enqueues a new survey response locally as PENDING (offline-first). Returns immediately. */
class SubmitSurveyResponseUseCase(private val repository: SurveyResponseRepository) {
    suspend operator fun invoke(newResponse: NewSurveyResponse): Result<Unit> = repository.enqueue(newResponse)
}
