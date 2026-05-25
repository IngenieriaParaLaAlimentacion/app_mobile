package ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.auth.AuthFailure
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.usecase.auth.RequestPasswordRecoveryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val requestRecovery: RequestPasswordRecoveryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.EmailChanged ->
                _state.update { it.copy(email = action.value, fieldError = null) }

            ForgotPasswordAction.Submit -> submit()
            ForgotPasswordAction.DismissError -> _state.update { it.copy(submitError = null) }
            ForgotPasswordAction.Back ->
                _state.update { it.copy(destination = ForgotPasswordDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        if (emailError != null) {
            _state.update { it.copy(fieldError = emailError) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            requestRecovery(email = current.email.trim()).fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isSent = true) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, submitError = throwable.toUserMessage())
                    }
                },
            )
        }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Ingresá tu email."
        !EMAIL_REGEX.matches(email.trim()) -> "Ingresá un email válido."
        else -> null
    }

    private fun Throwable.toUserMessage(): String {
        val error = (this as? AuthFailure)?.error
        return when (error) {
            AuthError.Network ->
                "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            AuthError.AccountPendingValidation,
            AuthError.InvalidCredentials,
            AuthError.InvalidOrExpiredToken,
            AuthError.WeakPassword,
            is AuthError.Unknown,
            null,
                -> "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
