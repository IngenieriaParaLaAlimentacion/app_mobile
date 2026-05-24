package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GetStartedViewModel : ViewModel() {

    private val _state = MutableStateFlow(GetStartedState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: GetStartedAction) {
        when (action) {
            GetStartedAction.SignUpClick ->
                _state.update { it.copy(destination = GetStartedDestination.NavigateToRegister) }
            GetStartedAction.LogInClick -> Unit
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
