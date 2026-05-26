package ar.com.ingenieriaalimentacion.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.SignOutClick -> performSignOut()
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun performSignOut() {
        if (_state.value.isSigningOut) return
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            signOut()
            _state.update {
                it.copy(
                    isSigningOut = false,
                    destination = SettingsDestination.LoggedOut,
                )
            }
        }
    }
}
