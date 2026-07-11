package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

fun Map<String, SurveyAnswerValue>.toAnswersJson(): Map<String, JsonElement> = mapValues { (_, v) ->
    when (v) {
        is SurveyAnswerValue.SingleChoice -> JsonPrimitive(v.optionId)
        is SurveyAnswerValue.MultipleChoice -> JsonArray(v.optionIds.map { JsonPrimitive(it) })
        is SurveyAnswerValue.Numeric -> JsonPrimitive(v.value)
        is SurveyAnswerValue.Text -> JsonPrimitive(v.value)
        is SurveyAnswerValue.DateAnswer -> JsonPrimitive(v.isoDate)
    }
}

/** Answers alone are ambiguous (a bare JSON string could be text/date/single-choice) — must
 *  be interpreted against the owning survey's question list. Unknown/missing questions are
 *  skipped rather than throwing, so a stale cached survey definition degrades gracefully. */
fun Map<String, JsonElement>.toAnswerValues(questions: List<SurveyQuestion>): Map<String, SurveyAnswerValue> {
    val byId = questions.associateBy { it.id }
    return mapNotNull { (questionId, element) ->
        val type = byId[questionId]?.responseType ?: return@mapNotNull null
        val value = when (type) {
            SurveyResponseType.SingleChoice -> SurveyAnswerValue.SingleChoice(element.jsonPrimitive.content)
            SurveyResponseType.MultipleChoice ->
                SurveyAnswerValue.MultipleChoice(element.jsonArray.map { it.jsonPrimitive.content })
            SurveyResponseType.Numeric -> SurveyAnswerValue.Numeric(element.jsonPrimitive.double)
            SurveyResponseType.Text -> SurveyAnswerValue.Text(element.jsonPrimitive.content)
            SurveyResponseType.Date -> SurveyAnswerValue.DateAnswer(element.jsonPrimitive.content)
        }
        questionId to value
    }.toMap()
}
