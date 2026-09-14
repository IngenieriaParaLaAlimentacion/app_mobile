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

/**
 * Validates the type/nesting invariant and maps to the matching entry.
 *
 * Returns **null** for a type this build does not know about — `food_intake`, `food_outflow`, or
 * anything the server adds later. It used to `error(...)`, and that throw escaped: in
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectHistoryRepositoryImpl] the `runCatching` wraps
 * only the HTTP call, so the mapping runs outside it and an unknown type crashed instead of failing
 * the `Result`. Every call site filters by `type` today, so nothing was live-at-risk — but an
 * additive backend change must never be able to crash an already-installed build, and a feed the
 * client can only partially understand is a feed it should render partially, not refuse. Still
 * throws on a *malformed* item of a known type (`checkNotNull`), which is a real contract break
 * worth surfacing.
 */
internal fun ActivityItemResponse.toParsedEntry(): ParsedActivityItem? {
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
        else -> null
    }
}
