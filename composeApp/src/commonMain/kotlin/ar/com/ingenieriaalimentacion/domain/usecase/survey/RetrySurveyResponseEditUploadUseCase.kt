package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository

class RetrySurveyResponseEditUploadUseCase(private val repository: SurveyResponseEditRepository) {
    suspend operator fun invoke(editId: String): Result<Unit> = repository.retry(editId)
}
