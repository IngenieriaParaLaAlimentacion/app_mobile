package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.di.KoinGlobalContext
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("PushTokenBridge")
object PushTokenBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @kotlin.native.ObjCName("handleNewToken")
    fun handleNewToken(token: String) {
        if (!KoinGlobalContext.isInitialized || token.isBlank()) return
        val koin = KoinGlobalContext.koin()
        val lifecycle = koin.get<SessionLifecycle>()
        val lease = lifecycle.leaseOrNull() ?: return
        scope.launch(lease) {
            if (lifecycle.sessionFor(lease) != null) koin.get<RegisterPushDeviceUseCase>()(token)
        }
    }
}
