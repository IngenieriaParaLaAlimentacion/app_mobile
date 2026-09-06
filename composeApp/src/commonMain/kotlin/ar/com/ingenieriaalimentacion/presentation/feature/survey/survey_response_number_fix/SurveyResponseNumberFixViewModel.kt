package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RESPONSE_NUMBER_RANGE
import ar.com.ingenieriaalimentacion.domain.usecase.survey.UpdateSurveyResponseNumberUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SurveyResponseNumberFixViewModel(
    private val route: Route.SurveyResponseNumberFix,
    private val updateResponseNumber: UpdateSurveyResponseNumberUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(SurveyResponseNumberFixState(route.surveyName))
    val state = _state
    fun onAction(action: SurveyResponseNumberFixAction) = when (action) {
        is SurveyResponseNumberFixAction.NumberChanged -> _state.update { it.copy(number = it.number.onChange(action.value.filter(Char::isDigit), validator)) }
        is SurveyResponseNumberFixAction.NumberFocusChanged -> _state.update { it.copy(number = it.number.onFocus(action.focused, validator)) }
        SurveyResponseNumberFixAction.Back -> _state.update { it.copy(destination = SurveyResponseNumberFixDestination.Back) }
        SurveyResponseNumberFixAction.Submit -> submit()
    }
    fun clearDestination() = _state.update { it.copy(destination = null) }
    private fun submit() {
        val state = _state.value.copy(number = _state.value.number.showError(validator))
        val number = state.number.value.toIntOrNull()
        if (number == null || !state.canSubmit) { _state.value = state; return }
        _state.value = state.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch { updateResponseNumber(route.localId, number).fold(
            onSuccess = { syncCoordinator.requestSync(); _state.update { it.copy(isSubmitting = false, destination = SurveyResponseNumberFixDestination.Back) } },
            onFailure = { error -> _state.update { it.copy(isSubmitting = false, submitError = if (error is SurveyFeatureFailure) "Ya tenés otra respuesta con ese número en esta encuesta." else "No se pudo guardar el número.") } },
        ) }
    }
    private val validator: Validator = Rules.of(Rules.required("Ingresá el número de respuesta."), Rules.digitsInRange(RESPONSE_NUMBER_RANGE, "Ingresá un número entre 1 y 999999."))
}
