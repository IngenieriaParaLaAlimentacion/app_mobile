package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditStatus
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository
import kotlinx.coroutines.flow.Flow

class ObserveSurveyResponseEditStatusUseCase(private val repository: SurveyResponseEditRepository) {
    operator fun invoke(responseId: String): Flow<SurveyResponseEditStatus?> = repository.observeEditStatus(responseId)
}
