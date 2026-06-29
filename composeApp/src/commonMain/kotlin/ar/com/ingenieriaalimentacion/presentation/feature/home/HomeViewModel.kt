package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Merges the two local upload queues + connectivity into a derived [HomeState] (via [deriveState]),
 * and loads the profile once (best-effort) for the greeting. Sync state is *derived* from the queues
 * — the [SyncCoordinator] exposes no observable state, only a fire-and-forget [SyncCoordinator.requestSync].
 */
class HomeViewModel(
    observeSubjectUploads: ObserveSubjectUploadsUseCase,
    observeMeasurements: ObserveMeasurementsUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val syncCoordinator: SyncCoordinator,
    connectivity: ConnectivityObserver,
    private val now: () -> Long,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        // Identity (best-effort; offline -> generic greeting, never blocks the screen).
        viewModelScope.launch {
            getMyProfile().onSuccess { profile ->
                _state.update { it.copy(greetingName = profile.name, roleLabel = roleLabel(profile)) }
            }
        }
        // Reactive queue + connectivity -> derived state (identity + destination preserved).
        viewModelScope.launch {
            combine(
                observeSubjectUploads(),
                observeMeasurements(),
                connectivity.isOnline,
            ) { subjects, measurements, online ->
                deriveState(subjects, measurements, online, now())
            }.collect { derived ->
                _state.update {
                    derived.copy(
                        destination = it.destination,
                        greetingName = it.greetingName,
                        roleLabel = it.roleLabel,
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.NewMeasurementClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateMeasurement) }
            HomeAction.NewSubjectClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateSubject) }
            HomeAction.SyncClick -> syncCoordinator.requestSync()
            HomeAction.SeeAllActivityClick,
            HomeAction.AlertsClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToHistory) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
