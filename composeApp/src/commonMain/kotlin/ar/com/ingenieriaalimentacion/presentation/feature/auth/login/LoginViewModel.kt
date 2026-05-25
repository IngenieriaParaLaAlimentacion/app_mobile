package ar.com.ingenieriaalimentacion.presentation.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.auth.AuthFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.Credentials
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged ->
                _state.update {
                    it.copy(
                        email = action.value,
                        fieldErrors = it.fieldErrors.copy(email = null),
                    )
                }

            is LoginAction.PasswordChanged ->
                _state.update {
                    it.copy(
                        password = action.value,
                        fieldErrors = it.fieldErrors.copy(password = null),
                    )
                }

            LoginAction.Submit -> submit()
            LoginAction.DismissError -> _state.update { it.copy(submitError = null) }
            LoginAction.ForgotPasswordClick ->
                _state.update { it.copy(destination = LoginDestination.ForgotPassword) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        val errors = validate(current)
        if (errors != LoginFieldErrors()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            signIn(
                Credentials(
                    email = current.email.trim(),
                    password = current.password,
                ),
            ).fold(
                onSuccess = {
                    _state.update {
                        it.copy(isSubmitting = false, destination = LoginDestination.Home)
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validate(state: LoginState): LoginFieldErrors = LoginFieldErrors(
        email = when {
            state.email.isBlank() -> "Ingresá tu email."
            !EMAIL_REGEX.matches(state.email.trim()) -> "El email no es válido."
            else -> null
        },
        password = when {
            state.password.isBlank() -> "Ingresá tu contraseña."
            else -> null
        },
    )

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.InvalidCredentials ->
                "Email o contraseña incorrectos."
            AuthError.AccountPendingValidation ->
                "Tu cuenta está pendiente de aprobación."
            AuthError.Network ->
                "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            AuthError.InvalidOrExpiredToken, AuthError.WeakPassword,
            is AuthError.Unknown, null ->
                "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
