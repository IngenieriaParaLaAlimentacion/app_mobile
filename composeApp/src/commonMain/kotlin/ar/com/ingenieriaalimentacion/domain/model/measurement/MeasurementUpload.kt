package ar.com.ingenieriaalimentacion.domain.model.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/**
 * A locally-queued measurement plus its sync metadata. The [localId] is a v4 UUID that doubles as the
 * SQLDelight primary key AND the idempotency `id` sent to the backend. [bmi] and [alerts] are
 * server-computed and populated only once the row reaches [SyncState.Synced].
 */
data class MeasurementUpload(
    val localId: String,
    val subjectId: String,
    val subjectName: String,
    val subjectLastName: String,
    val subjectDni: Int,
    val establishmentId: String,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
    val measuredAt: String,             // ISO 8601 UTC (…Z)
    val syncState: SyncState,
    val errorMessage: String?,
    val attemptCount: Int,
    val bmi: Double?,
    val alerts: List<ClinicalAlert>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Backoff eligibility, identical to the subjects queue. Fresh rows sync immediately; retrying rows
 * wait an exponential window (2s,4s,8s,16s,30s-cap) plus deterministic per-row jitter
 * (`createdAt % 1000`, no RNG, stable across reads).
 */
fun MeasurementUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
