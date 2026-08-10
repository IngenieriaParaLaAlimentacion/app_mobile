package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.registration.RegistrationFailure
import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterFieldWorkerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RegisterViewModel(
    private val registerFieldWorker: RegisterFieldWorkerUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.EmailChanged ->
                _state.update { it.copy(email = action.value, fieldErrors = it.fieldErrors.copy(email = null)) }

            is RegisterAction.PasswordChanged ->
                _state.update { it.copy(password = action.value, fieldErrors = it.fieldErrors.copy(password = null)) }

            is RegisterAction.NameChanged ->
                _state.update { it.copy(name = action.value, fieldErrors = it.fieldErrors.copy(name = null)) }

            is RegisterAction.LastNameChanged ->
                _state.update { it.copy(lastName = action.value, fieldErrors = it.fieldErrors.copy(lastName = null)) }

            is RegisterAction.LocalityChanged ->
                _state.update { it.copy(locality = it.locality.onChange(action.value, localityValidator)) }

            is RegisterAction.LocalityFocusChanged ->
                _state.update { it.copy(locality = it.locality.onFocus(action.focused, localityValidator)) }

            is RegisterAction.DescriptionChanged ->
                _state.update { it.copy(description = it.description.onChange(action.value, descriptionValidator)) }

            is RegisterAction.DescriptionFocusChanged ->
                _state.update { it.copy(description = it.description.onFocus(action.focused, descriptionValidator)) }

            is RegisterAction.ContactPhoneChanged ->
                _state.update { it.copy(contactPhone = it.contactPhone.onChange(action.value, contactPhoneValidator)) }

            is RegisterAction.ContactPhoneFocusChanged ->
                _state.update { it.copy(contactPhone = it.contactPhone.onFocus(action.focused, contactPhoneValidator)) }

            RegisterAction.OpenEstablishmentSelector ->
                _state.update {
                    it.copy(
                        destination = RegisterDestination.SelectEstablishment(
                            Json.encodeToString(it.selectedEstablishments),
                        ),
                    )
                }

            is RegisterAction.EstablishmentsSelected ->
                _state.update { it.copy(selectedEstablishments = action.establishments) }

            RegisterAction.Submit -> submit()
            RegisterAction.DismissError -> _state.update { it.copy(submitError = null) }
            RegisterAction.Back -> _state.update { it.copy(destination = RegisterDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        // Re-validate + force-show the optional fields, so a submit reveals their errors too.
        _state.update {
            it.copy(
                locality = it.locality.showError(localityValidator),
                description = it.description.showError(descriptionValidator),
                contactPhone = it.contactPhone.showError(contactPhoneValidator),
            )
        }
        val current = _state.value
        val errors = validate(current)
        val optionalFieldsValid =
            current.locality.isValid && current.description.isValid && current.contactPhone.isValid
        if (errors != RegisterFieldErrors() || !optionalFieldsValid) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val registration = FieldWorkerRegistration(
                email = current.email.trim(),
                password = current.password,
                name = current.name.trim(),
                lastName = current.lastName.trim(),
                establishmentIds = current.selectedEstablishments.map { it.id },
                // Optional fields travel as null when left empty; the backend rejects "".
                locality = current.locality.value.trimmedOrNull(),
                description = current.description.value.trimmedOrNull(),
                contactPhone = current.contactPhone.value.trimmedOrNull(),
            )
            registerFieldWorker(registration).fold(
                onSuccess = {
                    // Keep isSubmitting = true until navigation tears down this screen, so the
                    // button doesn't flash back to idle before the screen changes.
                    _state.update { it.copy(destination = RegisterDestination.Success) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validate(state: RegisterState): RegisterFieldErrors = RegisterFieldErrors(
        email = when {
            state.email.isBlank() -> "Ingresá tu email."
            !EMAIL_REGEX.matches(state.email.trim()) -> "Ingresá un email válido."
            else -> null
        },
        password = when {
            state.password.isBlank() -> "Ingresá una contraseña."
            state.password.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            else -> null
        },
        name = if (state.name.isBlank()) "Ingresá tu nombre." else null,
        lastName = if (state.lastName.isBlank()) "Ingresá tu apellido." else null,
    )

    /** Optional fields go out as `null` rather than `""` — the backend enforces `min_length=1`. */
    private fun String.trimmedOrNull(): String? = trim().takeIf { it.isNotBlank() }

    private fun Throwable.toUserMessage(): String {
        val error = (this as? RegistrationFailure)?.error
        return when (error) {
            RegistrationError.UserAlreadyExists -> "Ya existe una cuenta registrada con este email."
            RegistrationError.EstablishmentNotFound -> "Alguno de los establecimientos seleccionados ya no existe."
            RegistrationError.InvalidData -> "Los datos ingresados no son válidos. Revisá el formulario."
            RegistrationError.Network -> "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            is RegistrationError.Unknown, null -> "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    // Optional fields: content-only rules (no `required`), so they pass while empty and
    // only complain once the user typed something that breaks a backend constraint.
    private val localityValidator: Validator =
        Rules.maxLength(MAX_LOCALITY_LENGTH, "Máximo $MAX_LOCALITY_LENGTH caracteres.")

    private val descriptionValidator: Validator =
        Rules.maxLength(MAX_DESCRIPTION_LENGTH, "Máximo $MAX_DESCRIPTION_LENGTH caracteres.")

    private val contactPhoneValidator: Validator = Rules.of(
        Rules.matches(PHONE_REGEX, "Ingresá un teléfono válido (dígitos, +, -, espacios y paréntesis)."),
        Rules.maxLength(MAX_PHONE_LENGTH, "Máximo $MAX_PHONE_LENGTH caracteres."),
    )

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_LOCALITY_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_PHONE_LENGTH = 20
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /** Mirrors the backend's `contact_phone` pattern: digits, `+`, `-`, spaces and parentheses. */
        val PHONE_REGEX = Regex("^[0-9+\\-\\s()]+$")
    }
}
