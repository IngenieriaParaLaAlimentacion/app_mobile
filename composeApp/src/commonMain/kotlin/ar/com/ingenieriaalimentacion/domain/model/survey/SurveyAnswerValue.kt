package ar.com.ingenieriaalimentacion.domain.model.survey

/** One answer, typed per the owning question's [SurveyResponseType]. Keyed by question id
 *  in [ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse.answers]. */
sealed interface SurveyAnswerValue {
    data class SingleChoice(val optionId: String) : SurveyAnswerValue
    data class MultipleChoice(val optionIds: List<String>) : SurveyAnswerValue
    data class Numeric(val value: Double) : SurveyAnswerValue
    data class Text(val value: String) : SurveyAnswerValue
    /** ISO-8601 date string, e.g. "2026-07-10". */
    data class DateAnswer(val isoDate: String) : SurveyAnswerValue
}
