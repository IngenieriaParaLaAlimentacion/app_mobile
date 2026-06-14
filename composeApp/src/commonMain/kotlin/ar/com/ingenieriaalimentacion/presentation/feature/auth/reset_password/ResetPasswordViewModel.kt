package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.auth.AuthFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ResetPasswordUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    private val route: Route.ResetPassword,
    private val resetPassword: ResetPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.NewPasswordChanged ->
                _state.update {
                    it.copy(
                        newPassword = action.value,
                        fieldErrors = it.fieldErrors.copy(newPassword = null),
                    )
                }

            is ResetPasswordAction.ConfirmPasswordChanged ->
                _state.update {
                    it.copy(
                        confirmPassword = action.value,
                        fieldErrors = it.fieldErrors.copy(confirmPassword = null),
                    )
                }

            ResetPasswordAction.Submit -> submit()
            ResetPasswordAction.DismissError -> _state.update { it.copy(submitError = null) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        val errors = validate(current)
        if (errors != ResetPasswordFieldErrors()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            resetPassword(token = route.token, newPassword = current.newPassword).fold(
                onSuccess = {
                    // Keep isSubmitting = true until navigation tears down this screen, so the
                    // button doesn't flash back to idle before the screen changes.
                    _state.update { it.copy(destination = ResetPasswordDestination.Success) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validate(state: ResetPasswordState): ResetPasswordFieldErrors = ResetPasswordFieldErrors(
        newPassword = when {
            state.newPassword.isBlank() -> "Ingresá una contraseña."
            state.newPassword.length < MIN_PASSWORD_LENGTH ->
                "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            else -> null
        },
        confirmPassword = when {
            state.confirmPassword.isBlank() -> "Repetí la contraseña."
            state.confirmPassword != state.newPassword -> "Las contraseñas no coinciden."
            else -> null
        },
    )

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.InvalidOrExpiredToken ->
                "El enlace expiró o ya fue utilizado. Pedí uno nuevo."
            AuthError.WeakPassword ->
                "La contraseña no cumple los requisitos mínimos."
            AuthError.Network ->
                "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            AuthError.InvalidCredentials, AuthError.AccountPendingValidation,
            is AuthError.Unknown, null ->
                "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
