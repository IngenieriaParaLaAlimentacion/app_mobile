package ar.com.ingenieriaalimentacion.presentation.feature.survey.common

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseType
import kotlinx.datetime.LocalDate

/**
 * Per-question UI answer buffer, keyed by question id in the response-form ViewModels.
 * [SurveyResponseType.Text]/[SurveyResponseType.Numeric]/[SurveyResponseType.Date] use
 * blur-aware [TextFieldState] validation (CLAUDE.md's pattern); [SurveyResponseType.SingleChoice]/
 * [SurveyResponseType.MultipleChoice] have no natural "blur", so their error only force-shows on
 * Submit — mirrors `CreateSubjectScreen`'s submit-only `sexError`.
 */
sealed interface SurveyAnswerFieldState {
    data class Text(val field: TextFieldState = TextFieldState()) : SurveyAnswerFieldState
    data class Numeric(val field: TextFieldState = TextFieldState()) : SurveyAnswerFieldState

    /** [field]'s value is the raw `DDMMYYYY` digit buffer — same idiom as
     *  `CreateSubjectScreen`'s birth-date field. */
    data class DateAnswer(val field: TextFieldState = TextFieldState()) : SurveyAnswerFieldState
    data class SingleChoice(val selectedId: String? = null, val error: String? = null) : SurveyAnswerFieldState
    data class MultipleChoice(
        val selectedIds: Set<String> = emptySet(),
        val error: String? = null,
    ) : SurveyAnswerFieldState
}

fun SurveyQuestion.initialFieldState(): SurveyAnswerFieldState = when (responseType) {
    SurveyResponseType.SingleChoice -> SurveyAnswerFieldState.SingleChoice()
    SurveyResponseType.MultipleChoice -> SurveyAnswerFieldState.MultipleChoice()
    SurveyResponseType.Numeric -> SurveyAnswerFieldState.Numeric()
    SurveyResponseType.Text -> SurveyAnswerFieldState.Text()
    SurveyResponseType.Date -> SurveyAnswerFieldState.DateAnswer()
}

/** Prefills a field-state map from already-known [answers] (the edit screen's pre-fill case). */
fun List<SurveyQuestion>.toFieldStates(answers: Map<String, SurveyAnswerValue>): Map<String, SurveyAnswerFieldState> =
    associate { question ->
        val value = answers[question.id]
        val state = when (value) {
            is SurveyAnswerValue.SingleChoice -> SurveyAnswerFieldState.SingleChoice(selectedId = value.optionId)
            is SurveyAnswerValue.MultipleChoice ->
                SurveyAnswerFieldState.MultipleChoice(selectedIds = value.optionIds.toSet())
            is SurveyAnswerValue.Numeric -> SurveyAnswerFieldState.Numeric(
                field = TextFieldState(value = formatNumber(value.value)),
            )
            is SurveyAnswerValue.Text -> SurveyAnswerFieldState.Text(field = TextFieldState(value = value.value))
            is SurveyAnswerValue.DateAnswer -> SurveyAnswerFieldState.DateAnswer(
                field = TextFieldState(value = value.isoDate.toDateDigits()),
            )
            null -> question.initialFieldState()
        }
        question.id to state
    }

fun SurveyQuestion.validator(): Validator = { value ->
    val blank = value.isBlank()
    when {
        blank && isRequired -> REQUIRED_MESSAGE
        blank -> null
        responseType == SurveyResponseType.Numeric && !NUMERIC_REGEX.matches(value.trim()) ->
            "Ingresá un número válido."
        responseType == SurveyResponseType.Date && (value.length != 8 || parseDate(value) == null) ->
            "Formato esperado DD/MM/AAAA."
        else -> null
    }
}

/** Force-shows every field's error (submit sweep) and computes the missing-required error for
 *  choice questions (no natural blur to trigger it earlier). */
fun SurveyQuestion.forceShowError(state: SurveyAnswerFieldState): SurveyAnswerFieldState {
    val validator = validator()
    return when (state) {
        is SurveyAnswerFieldState.Text -> state.copy(field = state.field.showError(validator))
        is SurveyAnswerFieldState.Numeric -> state.copy(field = state.field.showError(validator))
        is SurveyAnswerFieldState.DateAnswer -> state.copy(field = state.field.showError(validator))
        is SurveyAnswerFieldState.SingleChoice -> state.copy(
            error = if (isRequired && state.selectedId == null) REQUIRED_MESSAGE else null,
        )
        is SurveyAnswerFieldState.MultipleChoice -> state.copy(
            error = if (isRequired && state.selectedIds.isEmpty()) REQUIRED_MESSAGE else null,
        )
    }
}

/** Submit-time sweep: force-shows every question's error (the submit ViewModels in
 *  survey_response_form/ and survey_response_edit/ share this exact shape). */
fun forceShowAllErrors(
    questions: List<SurveyQuestion>,
    answers: Map<String, SurveyAnswerFieldState>,
): Map<String, SurveyAnswerFieldState> = questions.associate { question ->
    question.id to question.forceShowError(answers[question.id] ?: question.initialFieldState())
}

fun SurveyQuestion.isFieldValid(state: SurveyAnswerFieldState): Boolean = when (state) {
    is SurveyAnswerFieldState.Text -> state.field.isValid
    is SurveyAnswerFieldState.Numeric -> state.field.isValid
    is SurveyAnswerFieldState.DateAnswer -> state.field.isValid
    is SurveyAnswerFieldState.SingleChoice -> !isRequired || state.selectedId != null
    is SurveyAnswerFieldState.MultipleChoice -> !isRequired || state.selectedIds.isNotEmpty()
}

/** Builds the typed [SurveyAnswerValue] map for [ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse]/
 *  an edit payload. A blank-and-optional question is simply omitted — an absent key is a valid
 *  "not answered" per the guide, since presence is only enforced for required questions. */
fun buildAnswers(
    questions: List<SurveyQuestion>,
    states: Map<String, SurveyAnswerFieldState>,
): Map<String, SurveyAnswerValue> = questions.mapNotNull { question ->
    val state = states[question.id] ?: return@mapNotNull null
    val value = when (state) {
        is SurveyAnswerFieldState.Text ->
            state.field.value.trim().takeIf { it.isNotBlank() }?.let { SurveyAnswerValue.Text(it) }
        is SurveyAnswerFieldState.Numeric ->
            state.field.value.trim().replace(',', '.').toDoubleOrNull()?.let { SurveyAnswerValue.Numeric(it) }
        is SurveyAnswerFieldState.DateAnswer ->
            parseDate(state.field.value)?.let { SurveyAnswerValue.DateAnswer(it.toString()) }
        is SurveyAnswerFieldState.SingleChoice -> state.selectedId?.let { SurveyAnswerValue.SingleChoice(it) }
        is SurveyAnswerFieldState.MultipleChoice ->
            state.selectedIds.takeIf { it.isNotEmpty() }?.let { SurveyAnswerValue.MultipleChoice(it.toList()) }
    }
    value?.let { question.id to it }
}.toMap()

private fun parseDate(input: String): LocalDate? {
    if (input.length != 8 || !input.all(Char::isDigit)) return null
    return runCatching {
        LocalDate(
            year = input.substring(4, 8).toInt(),
            monthNumber = input.substring(2, 4).toInt(),
            dayOfMonth = input.substring(0, 2).toInt(),
        )
    }.getOrNull()
}

private fun String.toDateDigits(): String {
    val date = runCatching { LocalDate.parse(this) }.getOrNull() ?: return ""
    val d = date.dayOfMonth.toString().padStart(2, '0')
    val m = date.monthNumber.toString().padStart(2, '0')
    return "$d$m${date.year}"
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private const val REQUIRED_MESSAGE = "Este campo es obligatorio."

/** Accepts "5", "5.5", and a leading-dot ".5" (a natural way to type a small decimal) — anything
 *  [ar.com.ingenieriaalimentacion.domain.util.sanitizeDecimalInput] can produce that also parses
 *  via `toDoubleOrNull()` in [buildAnswers]. Rejects an incomplete trailing-dot value like "5.". */
private val NUMERIC_REGEX = Regex("^\\d*\\.?\\d+$")
