package ar.com.ingenieriaalimentacion.domain.model.notification

data class StaleEstablishmentPush(
    val establishmentId: String,
    val staleDays: Int,
)
