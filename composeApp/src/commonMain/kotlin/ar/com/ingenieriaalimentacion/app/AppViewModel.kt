package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.auth.SessionEnded
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.SelectEstablishmentFromPushUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(
    initializeAuth: InitializeAuthUseCase,
    lifecycle: SessionLifecycle,
    syncCoordinator: SyncCoordinator,
    registerPushDevice: RegisterPushDeviceUseCase,
    selectEstablishmentFromPush: SelectEstablishmentFromPushUseCase,
) : ViewModel() {

    val sessionStatus = lifecycle.status

    private val _pushDestinations = Channel<AppPushDestination>(Channel.BUFFERED)
    val pushDestinations = _pushDestinations.receiveAsFlow()

    init {
        PushOpenBridge.bind(sessionStatus)
        viewModelScope.launch { initializeAuth() }
        syncCoordinator.start()
        viewModelScope.launch {
            sessionStatus
                .map { (it as? SessionStatus.Authenticated)?.generation }
                .distinctUntilChanged()
                .collectLatest { generation ->
                    val lease = lifecycle.leaseOrNull()?.takeIf { it.generation == generation }
                        ?: return@collectLatest
                    withContext(lease) { registerPushDevice() }
                }
        }
        viewModelScope.launch {
            val pushSession = sessionStatus.distinctUntilChanged { previous, next ->
                previous::class == next::class &&
                    (previous as? SessionStatus.Authenticated)?.generation ==
                    (next as? SessionStatus.Authenticated)?.generation
            }
            combine(pushSession, PushOpenBridge.pushes) { status, request -> status to request }
                .collectLatest { (status, request) ->
                    if (request == null) return@collectLatest
                    val lease = lifecycle.leaseOrNull()
                    if (lease == null) {
                        if (request.generation != null || status == SessionStatus.Closing ||
                            status == SessionStatus.AccountBlocked
                        ) {
                            PushOpenBridge.consume(request)
                        }
                        return@collectLatest
                    }
                    if (request.generation != null && request.generation != lease.generation) {
                        PushOpenBridge.consume(request)
                        return@collectLatest
                    }
                    try {
                        val selected = withContext(lease) {
                            selectEstablishmentFromPush(
                                request.push.establishmentId,
                                requireMembership = request.generation == null,
                            )
                        }
                        if (selected && lifecycle.sessionFor(lease) != null) {
                            _pushDestinations.send(AppPushDestination.FoodStock(lease.generation))
                        }
                    } catch (_: SessionEnded) {
                        // The graph and data stores already stopped accepting this lease.
                    } finally {
                        PushOpenBridge.consume(request)
                    }
                }
        }
    }
}
