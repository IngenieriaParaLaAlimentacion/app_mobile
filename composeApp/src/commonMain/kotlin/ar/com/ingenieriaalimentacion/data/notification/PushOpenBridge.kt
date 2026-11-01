package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.notification.StaleEstablishmentPush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("PushOpenBridge")
object PushOpenBridge {
    private val pending = MutableStateFlow<StaleEstablishmentPush?>(null)
    val pushes = pending.asStateFlow()

    fun handle(data: Map<String, String>) {
        StaleEstablishmentPushParser.parse(data)?.let { pending.value = it }
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

    fun consume(expected: StaleEstablishmentPush) {
        pending.compareAndSet(expected, null)
    }
}
