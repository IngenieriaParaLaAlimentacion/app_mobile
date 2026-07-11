package ar.com.ingenieriaalimentacion.presentation.feature.survey.common

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import kotlinx.datetime.LocalDate

/** Read-only display text for one answer, resolving choice ids to their option labels via
 *  [question]'s own option list. Used by the response-detail screen. */
fun formatAnswerForDisplay(value: SurveyAnswerValue, question: SurveyQuestion): String = when (value) {
    is SurveyAnswerValue.SingleChoice ->
        question.options.firstOrNull { it.id == value.optionId }?.text ?: value.optionId
    is SurveyAnswerValue.MultipleChoice -> value.optionIds.joinToString(", ") { id ->
        question.options.firstOrNull { it.id == id }?.text ?: id
    }
    is SurveyAnswerValue.Numeric -> formatAnswerNumber(value.value)
    is SurveyAnswerValue.Text -> value.value
    is SurveyAnswerValue.DateAnswer -> value.isoDate.toDisplayDate()
}

private fun formatAnswerNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun String.toDisplayDate(): String {
    val date = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
    val d = date.dayOfMonth.toString().padStart(2, '0')
    val m = date.monthNumber.toString().padStart(2, '0')
    return "$d/$m/${date.year}"
}
