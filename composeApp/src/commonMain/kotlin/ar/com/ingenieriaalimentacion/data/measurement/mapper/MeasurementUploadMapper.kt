package ar.com.ingenieriaalimentacion.data.measurement.mapper

import ar.com.ingenieriaalimentacion.data.db.PendingMeasurement
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lenient JSON used to (de)serialize the `alertsJson` column. */
private val alertsCodec = Json { ignoreUnknownKeys = true }

/** DB row → domain. Decodes the persisted alerts (absent/blank → empty). */
internal fun PendingMeasurement.toDomain(): MeasurementUpload = MeasurementUpload(
    localId = localId,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectLastName = subjectLastName,
    subjectDni = subjectDni.toInt(),
    establishmentId = establishmentId,
    establishmentName = establishmentName,
    weightKg = weightKg,
    heightCm = heightCm,
    measuredAt = measuredAt,
    syncState = syncState.toSyncState(),
    errorMessage = errorMessage,
    attemptCount = attemptCount.toInt(),
    bmi = bmi,
    alerts = alertsCodec.decodeAlerts(alertsJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Form input → a fresh PENDING upload row with the client-generated [id]. */
internal fun NewMeasurement.toPendingUpload(id: String, measuredAt: String, now: Long): MeasurementUpload =
    MeasurementUpload(
        localId = id,
        subjectId = subject.id,
        subjectName = subject.name,
        subjectLastName = subject.lastName,
        subjectDni = subject.dni,
        establishmentId = establishmentId,
        establishmentName = establishmentName,
        weightKg = weightKg,
        heightCm = heightCm,
        measuredAt = measuredAt,
        syncState = SyncState.Pending,
        errorMessage = null,
        attemptCount = 0,
        bmi = null,
        alerts = emptyList(),
        createdAt = now,
        updatedAt = now,
    )

/** Serialize alerts for the `alertsJson` column. */
internal fun encodeAlerts(alerts: List<ClinicalAlert>): String = alertsCodec.encodeToString(alerts)

internal fun MeasurementUpload.toHistoryEntry(): MeasurementHistoryEntry = MeasurementHistoryEntry(
    id = localId,
    subjectName = subjectName,
    subjectLastName = subjectLastName,
    subjectDni = subjectDni,
    establishmentName = establishmentName,
    weightKg = weightKg,
    heightCm = heightCm,
    measuredAt = measuredAt,
    syncState = syncState,
    errorMessage = errorMessage,
    bmi = bmi,
    alerts = alerts,
    isLocal = true,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Json.decodeAlerts(raw: String?): List<ClinicalAlert> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { decodeFromString<List<ClinicalAlert>>(raw) }.getOrElse { emptyList() }
}

private fun String.toSyncState(): SyncState = when (this) {
    "PENDING" -> SyncState.Pending
    "SYNCING" -> SyncState.Syncing
    "SYNCED" -> SyncState.Synced
    "MERGED" -> SyncState.Merged
    "FAILED" -> SyncState.Failed
    else -> error("Unknown syncState stored: $this")
}
