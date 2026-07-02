package ar.com.ingenieriaalimentacion.domain.model.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.serialization.Serializable

/** `@Serializable` so it round-trips through the `CachedActivityItem.payloadJson` column. */
@Serializable
data class MeasurementHistoryEntry(
    val id: String,
    val subjectName: String,
    val subjectLastName: String,
    val subjectDni: Int,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
    val measuredAt: String,
    val syncState: SyncState,     // always Synced for a remote-sourced entry
    val errorMessage: String?,    // FAILED local rows only
    val bmi: Double?,
    val alerts: List<ClinicalAlert>,
    val isLocal: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MeasurementHistoryPage(
    val items: List<MeasurementHistoryEntry>,
    val page: Int,
    val totalPages: Int,
)
