package ar.com.ingenieriaalimentacion.data.measurement.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    @SerialName("alert_type") val alertType: String,
    val severity: String,
    val detail: String,
)

/**
 * Success response (201 created or 200 idempotent replay). Carries the server-computed [bmi] and any
 * clinical [alerts], which are persisted onto the local row on sync.
 */
@Serializable
data class MeasurementResponse(
    val id: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("measured_at") val measuredAt: String,
    val bmi: Double? = null,
    val alerts: List<AlertDto> = emptyList(),
    @SerialName("stature_method") val statureMethod: String? = null,
    val assessments: List<AssessmentDto>? = null,
)

@Serializable
data class AssessmentDto(
    val indicator: String,
    val status: String,
    val classification: String? = null,
    @SerialName("z_score") val zScore: Double? = null,
    @SerialName("reference_version") val referenceVersion: String? = null,
    val alert: AssessmentAlertDto? = null,
)

@Serializable
data class AssessmentAlertDto(val severity: String, val detail: String)
