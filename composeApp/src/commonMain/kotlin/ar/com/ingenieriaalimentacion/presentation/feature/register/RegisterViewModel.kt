package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

            RegisterAction.OpenEstablishmentSelector ->
                _state.update {
                    it.copy(
                        destination = RegisterDestination.SelectEstablishment(
                            Json.encodeToString(it.selectedEstablishments),
                        ),
                    )
                }

            is RegisterAction.EstablishmentsSelected ->
                _state.update {
                    it.copy(
                        selectedEstablishments = action.establishments,
                        fieldErrors = it.fieldErrors.copy(establishments = null),
                    )
                }

            RegisterAction.Submit -> submit()
            RegisterAction.DismissError -> _state.update { it.copy(submitError = null) }
            RegisterAction.Back -> _state.update { it.copy(destination = RegisterDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        val errors = validate(current)
        if (errors != RegisterFieldErrors()) {
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
        establishments = if (state.selectedEstablishments.isEmpty()) "Seleccioná al menos un establecimiento." else null,
    )

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

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
