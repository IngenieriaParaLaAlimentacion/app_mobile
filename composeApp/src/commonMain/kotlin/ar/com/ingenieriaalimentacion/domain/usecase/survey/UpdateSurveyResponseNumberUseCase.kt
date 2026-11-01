package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository

val RESPONSE_NUMBER_RANGE = 1..999_999

class UpdateSurveyResponseNumberUseCase(private val repository: SurveyResponseRepository) {
    suspend fun queued(localId: String): SurveyResponseUpload? = repository.findQueued(localId)

    suspend operator fun invoke(localId: String, responseNumber: Int): Result<Unit> {
        if (responseNumber !in RESPONSE_NUMBER_RANGE) {
            return Result.failure(SurveyFeatureFailure(SurveyFeatureError.Unknown(null), null))
        }
        return repository.updateResponseNumber(localId, responseNumber)
    }
}
