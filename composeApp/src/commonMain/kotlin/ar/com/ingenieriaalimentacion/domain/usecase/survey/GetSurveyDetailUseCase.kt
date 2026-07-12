package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

/** Cache-first: a survey opened once while online is answerable fully offline afterward. */
class GetSurveyDetailUseCase(private val repository: SurveyRepository) {
    suspend operator fun invoke(surveyId: String): Result<SurveyDetail> = repository.getDetail(surveyId)
}
