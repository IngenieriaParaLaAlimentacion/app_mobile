package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationError
import ar.com.ingenieriaalimentacion.domain.model.auth.EmailVerificationFailure
import ar.com.ingenieriaalimentacion.domain.usecase.auth.VerifyEmailUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerifyEmailViewModel(
    private val route: Route.VerifyEmail,
    private val verifyEmail: VerifyEmailUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VerifyEmailState())
    val state = _state.asStateFlow()

    private var verification: Job? = null

    init {
        // The screen is only reachable by a person tapping the emailed link, so there is no
        // scanner to guard against: verify straight away. The ViewModel outlives recomposition
        // and rotation, so this runs once per visit.
        verify()
    }

    fun onAction(action: VerifyEmailAction) {
        when (action) {
            VerifyEmailAction.Retry -> verify()
            VerifyEmailAction.Continue ->
                _state.update { it.copy(destination = VerifyEmailDestination.Exit) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun verify() {
        if (verification?.isActive == true) return
        _state.update { it.copy(status = VerifyEmailStatus.Verifying) }
        verification = viewModelScope.launch {
            verifyEmail(route.token).fold(
                onSuccess = { _state.update { it.copy(status = VerifyEmailStatus.Verified) } },
                onFailure = { throwable ->
                    _state.update { it.copy(status = throwable.toStatus()) }
                },
            )
        }
    }

    private fun Throwable.toStatus(): VerifyEmailStatus =
        when ((this as? EmailVerificationFailure)?.error) {
            EmailVerificationError.TokenUsed -> VerifyEmailStatus.AlreadyUsed
            EmailVerificationError.TokenExpired -> VerifyEmailStatus.Expired
            EmailVerificationError.InvalidToken -> VerifyEmailStatus.InvalidLink
            // An untyped failure is treated as retryable: retrying cannot burn the token.
            EmailVerificationError.Network, null -> VerifyEmailStatus.ConnectionError
        }
}
