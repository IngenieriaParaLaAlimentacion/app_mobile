package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.lineCountLabel
import ar.com.ingenieriaalimentacion.domain.model.food.movementTitle
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads.alertTypeLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/** How many recent rows the Home activity list shows. */
private const val RECENT_LIMIT = 4

/**
 * Pure derivation of the reactive part of [HomeState] from the two local upload queues + the current
 * connectivity. Identity fields (greeting/role) are merged in by the ViewModel and intentionally left
 * at their defaults here. No Android/Compose deps so this is fully unit-testable.
 */
internal fun deriveState(
    subjects: List<SubjectHistoryEntry>,
    measurements: List<MeasurementHistoryEntry>,
    surveyResponses: List<SurveyResponseSummary> = emptyList(),
    foodMovements: List<FoodMovement> = emptyList(),
    online: Boolean,
    now: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): HomeState {
    val syncStates = subjects.map { it.syncState } + measurements.map { it.syncState }
    val syncingCount = syncStates.count { it == SyncState.Syncing }
    val pendingCount = syncStates.count { it == SyncState.Pending || it == SyncState.Syncing }
    val failedCount = syncStates.count { it == SyncState.Failed }

    val lastSyncedAt = (subjects.filter { it.syncState.isDone() }.map { it.updatedAt } +
        measurements.filter { it.syncState.isDone() }.map { it.updatedAt }).maxOrNull()

    val sync: SyncStatusUi = when {
        syncingCount > 0 -> SyncStatusUi.Syncing(syncingCount)
        failedCount > 0 -> SyncStatusUi.Failed(failedCount, pendingCount)
        pendingCount > 0 -> if (online) SyncStatusUi.PendingOnline(pendingCount)
        else SyncStatusUi.PendingOffline(pendingCount)
        online -> SyncStatusUi.AllSynced(lastSyncedAt?.let { formatLastSynced(it, timeZone) })
        else -> SyncStatusUi.OfflineClean
    }

    val startOfToday = startOfTodayMillis(now, timeZone)
    val todaySubjects = subjects.count { it.createdAt >= startOfToday }
    val todayMeasurements = measurements.count { it.createdAt >= startOfToday }
    val todaySurveyResponses = surveyResponses.count { eventAtMillis(it.eventAt) >= startOfToday }

    val recent = (
        subjects.map { it.toRecentUi() } +
            measurements.map { it.toRecentUi() } +
            surveyResponses.map { it.toRecentUi() } +
            foodMovements.map { it.toRecentUi() }
        )
        .sortedByDescending { it.createdAt }
        .take(RECENT_LIMIT)

    return HomeState(
        isOnline = online,
        sync = sync,
        todayMeasurements = todayMeasurements,
        todaySubjects = todaySubjects,
        todaySurveyResponses = todaySurveyResponses,
        recent = recent,
    )
}

/** Synced + Merged are the terminal "uploaded" states that count toward the last-synced timestamp. */
private fun SyncState.isDone(): Boolean = this == SyncState.Synced || this == SyncState.Merged

private fun SubjectHistoryEntry.toRecentUi(): RecentActivityUi {
    val (label, tone) = subjectSyncBadge(syncState)
    return RecentActivityUi(
        id = id,
        kind = RecentActivityUi.Kind.Subject,
        title = "$name $lastName",
        subtitle = "DNI $dni",
        value = null,
        badgeText = label,
        badgeTone = tone,
        createdAt = createdAt,
    )
}

private fun MeasurementHistoryEntry.toRecentUi(): RecentActivityUi {
    val topAlert = alerts.maxByOrNull { tonePriority(it.tone()) }
    val (label, tone) = if (topAlert != null) {
        alertTypeLabel(topAlert.alertType) to topAlert.tone()
    } else {
        measurementSyncBadge(syncState)
    }
    val synced = syncState == SyncState.Synced
    return RecentActivityUi(
        id = id,
        kind = RecentActivityUi.Kind.Measurement,
        title = "$subjectName $subjectLastName",
        subtitle = "Talla: ${formatMeasure(heightCm)} cm, Peso: ${formatMeasure(weightKg)} kg",
        value = bmi?.takeIf { synced }?.let { "IMC ${formatBmi(it)}" },
        badgeText = label,
        badgeTone = tone,
        createdAt = createdAt,
    )
}

private fun SurveyResponseSummary.toRecentUi(): RecentActivityUi {
    val (label, tone) = surveyResponseSyncBadge(syncState)
    return RecentActivityUi(
        id = id,
        kind = RecentActivityUi.Kind.SurveyResponse,
        title = surveyName,
        subtitle = targetLabel ?: "Anónima",
        value = null,
        badgeText = label,
        badgeTone = tone,
        createdAt = eventAtMillis(eventAt),
    )
}

/**
 * No sync badge: food movements have no offline queue, so every row is already on the server. The
 * badge slot carries the movement's size instead, which is the only other fact worth 20 characters.
 */
private fun FoodMovement.toRecentUi(): RecentActivityUi = RecentActivityUi(
    // Unique across both directions, same rule as the Alimentos LazyColumn key.
    id = "${direction.name}:$id",
    kind = when (direction) {
        FoodMovementDirection.Intake -> RecentActivityUi.Kind.FoodIntake
        FoodMovementDirection.Outflow -> RecentActivityUi.Kind.FoodOutflow
    },
    title = direction.movementTitle,
    subtitle = establishmentName,
    value = null,
    badgeText = lineCountLabel,
    badgeTone = AlBadgeTone.Neutral,
    createdAt = occurredAtMillis,
)

private fun subjectSyncBadge(state: SyncState): Pair<String, AlBadgeTone> = when (state) {
    SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
    SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
    SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
    SyncState.Merged -> "Ya existía" to AlBadgeTone.Warning
    SyncState.Failed -> "Error" to AlBadgeTone.Danger
}

private fun measurementSyncBadge(state: SyncState): Pair<String, AlBadgeTone> = when (state) {
    SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
    SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
    SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
    SyncState.Merged -> "Sincronizado" to AlBadgeTone.Success // unreachable for measurements
    SyncState.Failed -> "Error" to AlBadgeTone.Danger
}

private fun surveyResponseSyncBadge(state: SyncState): Pair<String, AlBadgeTone> = when (state) {
    SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
    SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
    SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
    SyncState.Merged -> "Sincronizado" to AlBadgeTone.Success // unreachable for survey responses
    SyncState.Failed -> "Error" to AlBadgeTone.Danger
}

/** ISO-8601 `eventAt` → epoch millis, matching SurveyResponseHistoryRepositoryImpl's own parsing;
 *  malformed input sorts to the very bottom instead of crashing derivation. */
private fun eventAtMillis(iso: String): Long =
    runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrDefault(0L)

/** Severity → tone, mirroring the history rows (`MeasurementUploadUi`). */
private fun ClinicalAlert.tone(): AlBadgeTone = when (severity.trim().lowercase()) {
    "critical", "severe", "high", "alta", "critica", "crítica" -> AlBadgeTone.Danger
    "moderate", "medium", "warning", "media", "moderada" -> AlBadgeTone.Warning
    else -> AlBadgeTone.Neutral
}

private fun tonePriority(tone: AlBadgeTone): Int = when (tone) {
    AlBadgeTone.Danger -> 3
    AlBadgeTone.Warning -> 2
    AlBadgeTone.Success -> 1
    AlBadgeTone.Neutral -> 0
}

/** Maps the signed-in user to a Spanish role label. */
internal fun roleLabel(profile: Profile): String =
    if (profile.isManager) "Responsable de establecimiento" else "Agente de campo"

/** Local-midnight epoch millis for the day containing [now]. */
internal fun startOfTodayMillis(now: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
    val date = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
    return date.atStartOfDayIn(timeZone).toEpochMilliseconds()
}

/** Absolute "dd/MM HH:mm" last-synced label (no ticking-clock dependency). */
private fun formatLastSynced(epochMillis: Long, timeZone: TimeZone): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val dd = dt.dayOfMonth.toString().padStart(2, '0')
    val mm = dt.monthNumber.toString().padStart(2, '0')
    val hh = dt.hour.toString().padStart(2, '0')
    val min = dt.minute.toString().padStart(2, '0')
    return "Última subida: $dd/$mm $hh:$min"
}

private fun formatBmi(v: Double): String = formatMeasure(v)

/** One-decimal, comma-separated number; drops a trailing `,0` (e.g. `175`, `70,5`). */
private fun formatMeasure(v: Double): String {
    val scaled = round(abs(v) * 10).toLong()
    val intPart = scaled / 10
    val fracPart = scaled % 10
    return buildString {
        if (v < 0 && scaled != 0L) append('-')
        append(intPart)
        if (fracPart != 0L) {
            append(',')
            append(fracPart)
        }
    }
}
