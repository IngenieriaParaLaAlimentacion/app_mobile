package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ClearLocalDataUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ClearEstablishmentCacheUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    initializeAuth: InitializeAuthUseCase,
    observeSessionStatus: ObserveSessionStatusUseCase,
    syncCoordinator: SyncCoordinator,
    clearEstablishmentCache: ClearEstablishmentCacheUseCase,
    clearLocalData: ClearLocalDataUseCase,
) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = observeSessionStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionStatus.Initializing,
        )

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
    }
}
