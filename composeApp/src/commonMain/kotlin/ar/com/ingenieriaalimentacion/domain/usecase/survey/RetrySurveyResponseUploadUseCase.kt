package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository

/** Re-queues a dead-lettered (FAILED) local response for the next sync pass. */
class RetrySurveyResponseUploadUseCase(private val repository: SurveyResponseRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.retry(localId)
}
