package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveSurveyResponseUploadsUseCase(private val repository: SurveyResponseHistoryRepository) {
    operator fun invoke(): Flow<List<SurveyResponseSummary>> = repository.observeLocalMerged()
}
