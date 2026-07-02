package ar.com.ingenieriaalimentacion.data.activity.dto

import ar.com.ingenieriaalimentacion.data.measurement.dto.AlertDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivitySubjectResponse(
    val id: String,
    val dni: Int,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val sex: SexDto,
)

/** The subject nested inside a measurement item — richer than [ActivitySubjectResponse]. */
@Serializable
data class ActivityMeasurementSubjectResponse(
    val id: String,
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String,
)

@Serializable
data class ActivityEstablishmentResponse(
    val id: String,
    val name: String,
    val city: String,
    val province: String,
)

@Serializable
data class ActivityMeasurementResponse(
    val id: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    val bmi: Double? = null,
    val subject: ActivityMeasurementSubjectResponse,
    val establishment: ActivityEstablishmentResponse,
    val alerts: List<AlertDto> = emptyList(),
    // `responsible` intentionally omitted — always the caller for /me/activity, never displayed.
)

/** Discriminated union on [type]: exactly one of [subject]/[measurement] is non-null. */
@Serializable
data class ActivityItemResponse(
    val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    val subject: ActivitySubjectResponse? = null,
    val measurement: ActivityMeasurementResponse? = null,
)

@Serializable
data class PaginatedActivityResponse(
    val items: List<ActivityItemResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
