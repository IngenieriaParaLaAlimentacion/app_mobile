package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.PrimaryClick -> Unit
            HomeAction.CreateSubjectClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateSubject) }
            HomeAction.LoadMeasurementClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToSelectSubject) }
            is HomeAction.SubjectPicked ->
                _state.update { it.copy(pickedSubject = action.subject) }
            HomeAction.DismissPicked ->
                _state.update { it.copy(pickedSubject = null) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
