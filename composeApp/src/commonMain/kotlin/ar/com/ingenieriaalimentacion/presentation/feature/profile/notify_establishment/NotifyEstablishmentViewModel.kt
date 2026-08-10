package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.profile.EstablishmentAbsenceFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.usecase.profile.NotifyEstablishmentAbsenceUseCase
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

    // Every field is optional; the backend only rejects values over 100 chars.
    private val fieldValidator: Validator = Rules.of(
        Rules.maxLength(MAX_FIELD_LENGTH, "Máximo $MAX_FIELD_LENGTH caracteres."),
    )

    fun onAction(action: NotifyEstablishmentAction) {
        when (action) {
            is NotifyEstablishmentAction.NameChanged ->
                _state.update { it.copy(name = it.name.onChange(action.value, fieldValidator)) }

            is NotifyEstablishmentAction.NameFocusChanged ->
                _state.update { it.copy(name = it.name.onFocus(action.focused, fieldValidator)) }

            is NotifyEstablishmentAction.AddressChanged ->
                _state.update { it.copy(address = it.address.onChange(action.value, fieldValidator)) }

            is NotifyEstablishmentAction.AddressFocusChanged ->
                _state.update { it.copy(address = it.address.onFocus(action.focused, fieldValidator)) }

            is NotifyEstablishmentAction.CityChanged ->
                _state.update { it.copy(city = it.city.onChange(action.value, fieldValidator)) }

            is NotifyEstablishmentAction.CityFocusChanged ->
                _state.update { it.copy(city = it.city.onFocus(action.focused, fieldValidator)) }

            is NotifyEstablishmentAction.ProvinceChanged ->
                _state.update { it.copy(province = it.province.onChange(action.value, fieldValidator)) }

            is NotifyEstablishmentAction.ProvinceFocusChanged ->
                _state.update { it.copy(province = it.province.onFocus(action.focused, fieldValidator)) }

            NotifyEstablishmentAction.SubmitClick -> submit()

            NotifyEstablishmentAction.BackClick ->
                _state.update { it.copy(destination = NotifyEstablishmentDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val validated = current.copy(
            name = current.name.showError(fieldValidator),
            address = current.address.showError(fieldValidator),
            city = current.city.showError(fieldValidator),
            province = current.province.showError(fieldValidator),
        )
        _state.value = validated
        if (!validated.isFormValid) return

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val notice = EstablishmentAbsenceNotice(
                name = validated.name.value,
                formattedAddress = validated.address.value,
                city = validated.city.value,
                province = validated.province.value,
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
                    _state.update { it.copy(isSubmitting = false, submitError = error) }
                },
            )
        }
    }

    private companion object {
        const val MAX_FIELD_LENGTH = 100
    }
}
