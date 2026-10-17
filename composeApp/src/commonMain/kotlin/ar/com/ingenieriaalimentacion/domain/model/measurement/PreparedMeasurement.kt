package ar.com.ingenieriaalimentacion.domain.model.measurement

data class PreparedMeasurement(
    val measurement: NewMeasurement,
    val requiresConfirmation: Boolean,
    val demographicsKnown: Boolean,
)
