package ar.com.ingenieriaalimentacion.data.measurement.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST body for a measurement. [id] is the client-generated UUID (idempotency key). No `bmi` — the
 * server computes it and returns it in [MeasurementResponse].
 */
@Serializable
data class MeasurementRequest(
    val id: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("measured_at") val measuredAt: String, // ISO 8601 UTC (…Z)
)
