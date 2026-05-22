package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.registration.RegistrationFailure
import ar.com.ingenieriaalimentacion.domain.model.registration.ManagerRegistration
import ar.com.ingenieriaalimentacion.domain.model.registration.RegistrationError
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterManagerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val getEstablishments: GetEstablishmentsUseCase,
    private val registerManager: RegisterManagerUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        loadEstablishments()
    }

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

            is RegisterAction.EstablishmentToggled -> toggleEstablishment(action.id)
            RegisterAction.Submit -> submit()
            RegisterAction.DismissError -> _state.update { it.copy(submitError = null) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun loadEstablishments() {
        _state.update { it.copy(isLoadingEstablishments = true) }
        viewModelScope.launch {
            getEstablishments().fold(
                onSuccess = { list ->
                    _state.update { it.copy(establishments = list, isLoadingEstablishments = false) }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingEstablishments = false,
                            submitError = "No se pudieron cargar los establecimientos. Intentá nuevamente.",
                        )
                    }
                },
            )
        }
    }

    private fun toggleEstablishment(id: String) {
        _state.update { current ->
            val selected = if (id in current.selectedEstablishmentIds) {
                current.selectedEstablishmentIds - id
            } else {
                current.selectedEstablishmentIds + id
            }
            current.copy(
                selectedEstablishmentIds = selected,
                fieldErrors = current.fieldErrors.copy(establishments = null),
            )
        }
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
            val registration = ManagerRegistration(
                email = current.email.trim(),
                password = current.password,
                name = current.name.trim(),
                lastName = current.lastName.trim(),
                establishmentIds = current.selectedEstablishmentIds.toList(),
            )
            registerManager(registration).fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, destination = RegisterDestination.Success) }
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
        establishments = if (state.selectedEstablishmentIds.isEmpty()) "Seleccioná al menos un establecimiento." else null,
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
