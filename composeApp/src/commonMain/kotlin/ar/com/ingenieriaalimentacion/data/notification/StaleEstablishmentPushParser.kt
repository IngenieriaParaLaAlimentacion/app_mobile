package ar.com.ingenieriaalimentacion.data.notification

import ar.com.ingenieriaalimentacion.domain.model.notification.StaleEstablishmentPush

object StaleEstablishmentPushParser {
    fun parse(data: Map<String, String>): StaleEstablishmentPush? {
        if (data["type"] != "stale_establishment") return null
        val establishmentId = data["establishment_id"]?.takeIf(String::isNotBlank) ?: return null
        val staleDays = data["stale_days"]?.toIntOrNull()?.takeIf { it in setOf(7, 14, 21) }
            ?: return null
        return StaleEstablishmentPush(establishmentId, staleDays)
    }
}
