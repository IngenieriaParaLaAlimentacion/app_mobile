package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.ObserveFoodMovementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
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
    observeSurveyResponseUploads: ObserveSurveyResponseUploadsUseCase,
    observeFoodMovements: ObserveFoodMovementsUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    private val getCurrentEstablishment: GetCurrentEstablishmentUseCase,
    private val setCurrentEstablishment: SetCurrentEstablishmentUseCase,
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
                _state.update {
                    it.copy(
                        greetingName = profile.name,
                        roleLabel = roleLabel(profile),
                    )
                }
            }
        }
        // Reactive queue + connectivity -> derived state (identity + destination preserved).
        viewModelScope.launch {
            // 5 flows — the LAST typed `combine` overload. A sixth source must NOT be added here:
            // group the reactive flows into a `HomeSources` value first, then combine that with
            // connectivity. This one fits only because the food repository already merges its two
            // cache flows (food_intake + food_outflow) into a single flow in the data layer.
            combine(
                observeSubjectUploads(),
                observeMeasurements(),
                observeSurveyResponseUploads(),
                observeFoodMovements(),
                connectivity.isOnline,
            ) { subjects, measurements, surveyResponses, foodMovements, online ->
                deriveState(subjects, measurements, surveyResponses, foodMovements, online, now())
            }.collect { derived ->
                _state.update {
                    derived.copy(
                        destination = it.destination,
                        greetingName = it.greetingName,
                        roleLabel = it.roleLabel,
                        currentEstablishment = it.currentEstablishment,
                        establishmentOptions = it.establishmentOptions,
                        showEstablishmentPicker = it.showEstablishmentPicker,
                    )
                }
            }
        }
        loadEstablishments()
    }

    private fun loadEstablishments() {
        viewModelScope.launch {
            getMyEstablishments().onSuccess { all ->
                val approved = all.filter { it.status == EstablishmentStatus.Approved }
                val current = getCurrentEstablishment(approved)
                _state.update { it.copy(establishmentOptions = approved, currentEstablishment = current) }
            }
            // Failure: leave establishmentOptions/currentEstablishment at their defaults — Home never
            // blocks on this (ADR-015); the picker just shows nothing to pick yet.
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.NewMeasurementClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateMeasurement) }
            HomeAction.NewSubjectClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateSubject) }
            HomeAction.SyncClick -> syncCoordinator.requestSync()
            HomeAction.SeeAllActivityClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToHistory) }
            HomeAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }
            HomeAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }
            is HomeAction.EstablishmentSelected -> {
                _state.update {
                    it.copy(currentEstablishment = action.establishment, showEstablishmentPicker = false)
                }
                viewModelScope.launch { setCurrentEstablishment(action.establishment) }
            }
            HomeAction.RespondSurveyClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToSurveyList) }

            HomeAction.NewFoodIntakeClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateFoodIntake) }

            HomeAction.NewFoodOutflowClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToCreateFoodOutflow) }

            HomeAction.FoodStockClick ->
                _state.update { it.copy(destination = HomeDestination.NavigateToFoodStock) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
