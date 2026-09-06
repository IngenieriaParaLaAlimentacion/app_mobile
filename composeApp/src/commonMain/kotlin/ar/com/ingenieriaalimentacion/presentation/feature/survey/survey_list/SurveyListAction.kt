package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey

sealed interface SurveyListAction {
    /** Carries the whole [Survey] — it already knows its `responseTarget`, so the reducer can route
     *  by target without a second lookup. */
    data class SurveyClick(val survey: Survey) : SurveyListAction

    /** A subject came back from the picker (or from inline creation) for the pending survey. */
    data class SubjectPicked(val subject: SubjectSummary) : SurveyListAction

    /** An establishment came back from the single-select picker for the pending survey. */
    data class EstablishmentPicked(val establishment: Establishment) : SurveyListAction

    data object LoadMore : SurveyListAction
    data object RetryClick : SurveyListAction
    data object BackClick : SurveyListAction
}
