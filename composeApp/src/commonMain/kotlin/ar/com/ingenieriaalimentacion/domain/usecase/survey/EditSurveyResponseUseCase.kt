package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository

/** Queues a full-replace edit for a response locally (offline-first). Returns immediately. */
class EditSurveyResponseUseCase(private val repository: SurveyResponseEditRepository) {
    suspend operator fun invoke(
        responseId: String,
        surveyId: String,
        surveyName: String,
        establishmentName: String,
        responseNumber: Int? = null,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit> =
        repository.enqueueEdit(responseId, surveyId, surveyName, establishmentName, responseNumber, answers, eventAtMs)
}
