package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.notification.StaleEstablishmentPush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("PushOpenBridge")
object PushOpenBridge {
    private val status = MutableStateFlow<StateFlow<SessionStatus>?>(null)
    private val pending = MutableStateFlow<OpenRequest?>(null)
    internal val pushes = pending.asStateFlow()

    internal fun bind(sessionStatus: StateFlow<SessionStatus>?) {
        status.value = sessionStatus
    }

    fun handle(data: Map<String, String>) {
        val push = StaleEstablishmentPushParser.parse(data) ?: return
        val current = status.value?.value
        if (current == SessionStatus.Closing || current == SessionStatus.AccountBlocked) return
        val generation = (current as? SessionStatus.Authenticated)?.generation
        pending.value = OpenRequest(push, generation)
    }

    @kotlin.native.ObjCName("handleStaleEstablishment")
    fun handleStaleEstablishment(establishmentId: String, staleDays: Int) {
        handle(
            mapOf(
                "type" to "stale_establishment",
                "establishment_id" to establishmentId,
                "stale_days" to staleDays.toString(),
            ),
        )
    }

    internal fun consume(expected: OpenRequest) {
        pending.compareAndSet(expected, null)
    }

    internal class OpenRequest(val push: StaleEstablishmentPush, val generation: String?)
}
