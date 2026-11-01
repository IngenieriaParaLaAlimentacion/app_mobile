package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentAbsenceFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.usecase.profile.NotifyEstablishmentAbsenceUseCase
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormReducer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotifyEstablishmentViewModel(
    private val notifyAbsence: NotifyEstablishmentAbsenceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(NotifyEstablishmentState())
    val state = _state.asStateFlow()

    // The backend requires name and address; city and province stay optional.
    // Every field is capped at 100 chars.
    private val maxLengthRule = Rules.maxLength(MAX_FIELD_LENGTH, "Máximo $MAX_FIELD_LENGTH caracteres.")
    private val nameValidator: Validator = Rules.of(
        Rules.required("Ingresá el nombre del comedor."),
        maxLengthRule,
    )
    private val addressValidator: Validator = Rules.of(
        Rules.required("Ingresá la dirección."),
        maxLengthRule,
    )
    private val optionalFieldValidator: Validator = Rules.of(maxLengthRule)

    fun onAction(action: NotifyEstablishmentAction) {
        when (action) {
            is NotifyEstablishmentAction.NameChanged ->
                _state.update { it.copy(name = it.name.onChange(action.value, nameValidator)) }

            is NotifyEstablishmentAction.NameFocusChanged ->
                _state.update { it.copy(name = it.name.onFocus(action.focused, nameValidator)) }

            is NotifyEstablishmentAction.AddressChanged ->
                _state.update { it.copy(address = it.address.onChange(action.value, addressValidator)) }

            is NotifyEstablishmentAction.AddressFocusChanged ->
                _state.update { it.copy(address = it.address.onFocus(action.focused, addressValidator)) }

            is NotifyEstablishmentAction.CityChanged ->
                _state.update { it.copy(city = it.city.onChange(action.value, optionalFieldValidator)) }

            is NotifyEstablishmentAction.CityFocusChanged ->
                _state.update { it.copy(city = it.city.onFocus(action.focused, optionalFieldValidator)) }

            is NotifyEstablishmentAction.ProvinceChanged ->
                _state.update { it.copy(province = it.province.onChange(action.value, optionalFieldValidator)) }

            is NotifyEstablishmentAction.ProvinceFocusChanged ->
                _state.update { it.copy(province = it.province.onFocus(action.focused, optionalFieldValidator)) }

            is NotifyEstablishmentAction.Characterization -> _state.update {
                it.copy(
                    characterization = CharacterizationFormReducer.reduce(it.characterization, action.action),
                    submitError = null,
                    hasInvalidCharacterization = false,
                )
            }

            NotifyEstablishmentAction.NextClick -> next()
            NotifyEstablishmentAction.SubmitClick -> submit(withCharacterization = true)
            NotifyEstablishmentAction.AnswerLaterClick -> submit(withCharacterization = false)

            NotifyEstablishmentAction.BackClick -> _state.update {
                if (it.isSubmitting) {
                    it
                } else if (it.step == NotifyEstablishmentStep.Characterization) {
                    // Everything typed in either step is kept.
                    it.copy(step = NotifyEstablishmentStep.Basics, submitError = null)
                } else {
                    it.copy(destination = NotifyEstablishmentDestination.Back)
                }
            }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun validateBasics(state: NotifyEstablishmentState) = state.copy(
        name = state.name.showError(nameValidator),
        address = state.address.showError(addressValidator),
        city = state.city.showError(optionalFieldValidator),
        province = state.province.showError(optionalFieldValidator),
    )

    private fun next() {
        val validated = validateBasics(_state.value)
        _state.value = if (validated.isFormValid) {
            validated.copy(step = NotifyEstablishmentStep.Characterization, submitError = null)
        } else {
            validated
        }
    }

    private fun submit(withCharacterization: Boolean) {
        val current = _state.value
        if (current.isSubmitting) return

        // Re-checked here too: a server error may have sent the user back with a field to fix.
        val validated = validateBasics(current)
        if (!validated.isFormValid) {
            _state.value = validated.copy(step = NotifyEstablishmentStep.Basics)
            return
        }

        val characterization = if (withCharacterization) {
            val form = CharacterizationFormReducer.showAllErrors(validated.characterization)
            if (!form.isValid) {
                _state.value = validated.copy(characterization = form, hasInvalidCharacterization = true)
                return
            }
            CharacterizationFormReducer.toCharacterization(form)
        } else {
            null
        }

        _state.value = validated.copy(isSubmitting = true, submitError = null, hasInvalidCharacterization = false)
        viewModelScope.launch {
            val notice = EstablishmentAbsenceNotice(
                name = validated.name.value,
                formattedAddress = validated.address.value,
                city = validated.city.value,
                province = validated.province.value,
                characterization = characterization,
            )
            notifyAbsence(notice).fold(
                onSuccess = {
                    // Keep isSubmitting = true until navigation tears down this screen, so the
                    // button doesn't flash back to idle before we pop back.
                    _state.update { it.copy(destination = NotifyEstablishmentDestination.Submitted) }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? EstablishmentAbsenceFailure)?.error
                        ?: EstablishmentAbsenceError.Unknown(throwable)
                    _state.update { it.withServerError(error).copy(isSubmitting = false, submitError = error) }
                },
            )
        }
    }

    /**
     * Routes a 422 to the input it names, without clearing anything: a ficha field stays on step 2,
     * a name/address field sends the user back to step 1 with that input flagged.
     */
    private fun NotifyEstablishmentState.withServerError(error: EstablishmentAbsenceError): NotifyEstablishmentState {
        val field = (error as? EstablishmentAbsenceError.Validation)?.field ?: return this
        if (field.startsWith(CHARACTERIZATION_PREFIX)) {
            return copy(
                step = NotifyEstablishmentStep.Characterization,
                characterization = CharacterizationFormReducer.withServerError(characterization, field),
            )
        }
        val flag = { state: TextFieldState -> state.copy(error = SERVER_INVALID, forceShow = true, isFocused = false) }
        val flagged = when (field) {
            "name" -> copy(name = flag(name))
            "formatted_address" -> copy(address = flag(address))
            "city" -> copy(city = flag(city))
            "province" -> copy(province = flag(province))
            else -> return this
        }
        return flagged.copy(step = NotifyEstablishmentStep.Basics)
    }

    private companion object {
        const val MAX_FIELD_LENGTH = 100
        const val CHARACTERIZATION_PREFIX = "characterization."
        const val SERVER_INVALID = "Este valor no es válido. Revisalo y volvé a enviar."
    }
}
