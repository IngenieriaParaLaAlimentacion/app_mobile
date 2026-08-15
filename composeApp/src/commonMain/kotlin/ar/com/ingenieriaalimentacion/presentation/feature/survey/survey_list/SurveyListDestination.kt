package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

sealed class SurveyListDestination {
    data object Back : SurveyListDestination()

    /** [targetId]/[targetLabel] are null for an anonymous survey, and for an establishment survey
     *  the form is expected to pre-fill from the Home default. */
    data class SurveyResponseForm(
        val surveyId: String,
        val targetId: String? = null,
        val targetLabel: String? = null,
    ) : SurveyListDestination()

    data object SelectSubject : SurveyListDestination()
    data object SelectEstablishment : SurveyListDestination()
}
