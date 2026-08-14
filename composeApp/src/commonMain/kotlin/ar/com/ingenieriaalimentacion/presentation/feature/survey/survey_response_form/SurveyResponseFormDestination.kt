package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form

sealed class SurveyResponseFormDestination {
    data object Back : SurveyResponseFormDestination()

    /** Target consumed — that subject/establishment can't answer this survey again, so there is
     *  nothing left to do here. Anonymous surveys never emit this; they loop in place. */
    data object Done : SurveyResponseFormDestination()
}
