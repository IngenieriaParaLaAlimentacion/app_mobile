package ar.com.ingenieriaalimentacion.presentation.feature.survey.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlChoiceGroup
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlChoiceOption
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlCheckboxGroup
import ar.com.ingenieriaalimentacion.core.design_system.components.input.AlInput
import ar.com.ingenieriaalimentacion.core.design_system.components.input.DateVisualTransformation
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyQuestion
import ar.com.ingenieriaalimentacion.domain.util.sanitizeDecimalInput

/**
 * Shared dynamic-question renderer for the fill-a-survey and edit-a-response flows. Renders one
 * field per [questions] entry by `responseType`, driven by [answers] (per-question UI buffer) and
 * [onAnswerChanged].
 */
@Composable
fun SurveyQuestionForm(
    questions: List<SurveyQuestion>,
    answers: Map<String, SurveyAnswerFieldState>,
    onAnswerChanged: (questionId: String, SurveyAnswerFieldState) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        questions.forEach { question ->
            val state = answers[question.id] ?: question.initialFieldState()
            val validator = question.validator()
            val label = if (question.isRequired) "${question.text} *" else question.text

            when (state) {
                is SurveyAnswerFieldState.SingleChoice -> AlChoiceGroup(
                    label = label,
                    options = question.options.map { AlChoiceOption(it.id, it.text) },
                    selectedId = state.selectedId,
                    onSelected = { optionId ->
                        onAnswerChanged(question.id, state.copy(selectedId = optionId, error = null))
                    },
                    error = state.error,
                    enabled = enabled,
                )

                is SurveyAnswerFieldState.MultipleChoice -> AlCheckboxGroup(
                    label = label,
                    options = question.options.map { AlChoiceOption(it.id, it.text) },
                    selectedIds = state.selectedIds,
                    onToggle = { optionId ->
                        val updated = if (optionId in state.selectedIds) {
                            state.selectedIds - optionId
                        } else {
                            state.selectedIds + optionId
                        }
                        onAnswerChanged(question.id, state.copy(selectedIds = updated, error = null))
                    },
                    error = state.error,
                    enabled = enabled,
                )

                is SurveyAnswerFieldState.Numeric -> AlInput(
                    label = label,
                    value = state.field.value,
                    onValueChange = { raw ->
                        onAnswerChanged(
                            question.id,
                            state.copy(field = state.field.onChange(sanitizeDecimalInput(raw), validator)),
                        )
                    },
                    onFocusChange = { focused ->
                        onAnswerChanged(question.id, state.copy(field = state.field.onFocus(focused, validator)))
                    },
                    error = state.field.displayError,
                    enabled = enabled,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                is SurveyAnswerFieldState.Text -> AlInput(
                    label = label,
                    value = state.field.value,
                    onValueChange = { raw ->
                        onAnswerChanged(question.id, state.copy(field = state.field.onChange(raw, validator)))
                    },
                    onFocusChange = { focused ->
                        onAnswerChanged(question.id, state.copy(field = state.field.onFocus(focused, validator)))
                    },
                    error = state.field.displayError,
                    enabled = enabled,
                    singleLine = false,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                )

                is SurveyAnswerFieldState.DateAnswer -> AlInput(
                    label = label,
                    value = state.field.value,
                    onValueChange = { raw ->
                        val digits = raw.filter(Char::isDigit).take(8)
                        onAnswerChanged(question.id, state.copy(field = state.field.onChange(digits, validator)))
                    },
                    onFocusChange = { focused ->
                        onAnswerChanged(question.id, state.copy(field = state.field.onFocus(focused, validator)))
                    },
                    error = state.field.displayError,
                    enabled = enabled,
                    scrollState = scrollState,
                    isImeVisible = isImeVisible,
                    placeholder = "DD/MM/AAAA",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                )
            }
        }
    }
}
