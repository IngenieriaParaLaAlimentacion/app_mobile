package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ClearLocalDataUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ClearEstablishmentCacheUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.SelectEstablishmentFromPushUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    initializeAuth: InitializeAuthUseCase,
    observeSessionStatus: ObserveSessionStatusUseCase,
    syncCoordinator: SyncCoordinator,
    clearEstablishmentCache: ClearEstablishmentCacheUseCase,
    clearLocalData: ClearLocalDataUseCase,
    registerPushDevice: RegisterPushDeviceUseCase,
    selectEstablishmentFromPush: SelectEstablishmentFromPushUseCase,
) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = observeSessionStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionStatus.Initializing,
        )

    private val _pushDestinations = Channel<AppPushDestination>(Channel.BUFFERED)
    val pushDestinations = _pushDestinations.receiveAsFlow()

    init {
        viewModelScope.launch { initializeAuth() }
        // Start the offline-first sync engine: crash recovery, connectivity collector, cleanup loop.
        syncCoordinator.start()
        // Explicit sign-out wipes ALL local data so the next user starts clean; a forced
        // (refresh-failure) logout only drops the establishment cache, preserving un-synced uploads.
        viewModelScope.launch {
            sessionStatus
                .filterIsInstance<SessionStatus.NotAuthenticated>()
                .collect { status ->
                    if (status.isSignOut) clearLocalData() else clearEstablishmentCache()
                }
        }
        viewModelScope.launch {
            sessionStatus
                .map { it is SessionStatus.Authenticated }
                .distinctUntilChanged()
                .filter { it }
                .collect { registerPushDevice() }
        }
        viewModelScope.launch {
            combine(sessionStatus, PushOpenBridge.pushes) { session, push -> session to push }
                .collect { (session, push) ->
                    if (session !is SessionStatus.Authenticated || push == null) return@collect
                    selectEstablishmentFromPush(push.establishmentId)
                    PushOpenBridge.consume(push)
                    _pushDestinations.send(AppPushDestination.FoodStock)
                }
        }
    }
}
