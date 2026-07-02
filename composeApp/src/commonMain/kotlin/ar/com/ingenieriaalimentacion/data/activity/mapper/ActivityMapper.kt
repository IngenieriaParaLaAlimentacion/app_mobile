package ar.com.ingenieriaalimentacion.data.activity.mapper

import ar.com.ingenieriaalimentacion.data.activity.dto.ActivityItemResponse
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain as alertToDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain as sexDtoToDomain
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlin.time.Instant

/** Sealed result of parsing one wire item, so callers can filter/branch without re-checking `type`. */
internal sealed interface ParsedActivityItem {
    data class SubjectEntry(val entry: SubjectHistoryEntry) : ParsedActivityItem
    data class MeasurementEntry(val entry: MeasurementHistoryEntry) : ParsedActivityItem
}

/** Validates the type/nesting invariant and maps to the matching entry. Throws on a malformed item. */
internal fun ActivityItemResponse.toParsedEntry(): ParsedActivityItem {
    val occurredAtMillis = Instant.parse(occurredAt).toEpochMilliseconds()
    return when (type) {
        "subject" -> {
            val s = checkNotNull(subject) { "activity item type=subject with no subject payload" }
            ParsedActivityItem.SubjectEntry(
                SubjectHistoryEntry(
                    id = s.id,
                    dni = s.dni,
                    sex = s.sex.sexDtoToDomain(),
                    name = s.name,
                    lastName = s.lastName,
                    syncState = SyncState.Synced,
                    canonicalId = null,
                    errorMessage = null,
                    isLocal = false,
                    createdAt = occurredAtMillis,
                    updatedAt = occurredAtMillis,
                ),
            )
        }
        "measurement" -> {
            val m = checkNotNull(measurement) { "activity item type=measurement with no measurement payload" }
            ParsedActivityItem.MeasurementEntry(
                MeasurementHistoryEntry(
                    id = m.id,
                    subjectName = m.subject.name,
                    subjectLastName = m.subject.lastName,
                    subjectDni = m.subject.dni,
                    establishmentName = m.establishment.name,
                    weightKg = m.weightKg,
                    heightCm = m.heightCm,
                    measuredAt = m.measuredAt,
                    syncState = SyncState.Synced,
                    errorMessage = null,
                    bmi = m.bmi,
                    alerts = m.alerts.map { it.alertToDomain() },
                    isLocal = false,
                    createdAt = occurredAtMillis,
                    updatedAt = occurredAtMillis,
                ),
            )
        }
        else -> error("unknown activity item type: $type")
    }
}
