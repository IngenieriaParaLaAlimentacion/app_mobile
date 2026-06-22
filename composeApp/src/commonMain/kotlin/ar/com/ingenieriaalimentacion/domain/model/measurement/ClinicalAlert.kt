package ar.com.ingenieriaalimentacion.domain.model.measurement

import kotlinx.serialization.Serializable

/**
 * A clinical alert computed server-side from a measurement (e.g. low/high BMI for the subject's
 * age/sex). Persisted onto the local row on a successful sync so the history (Historia 3) can show it.
 * `@Serializable` so it round-trips through the [ar.com.ingenieriaalimentacion.data.db.PendingMeasurement]
 * `alertsJson` column.
 */
@Serializable
data class ClinicalAlert(
    val alertType: String,
    val severity: String,
    val detail: String,
)
