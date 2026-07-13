package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

/**
 * Field-tool Home state. Identity fields ([greetingName]/[roleLabel]) are best-effort and load once
 * on entry; everything else is derived reactively from the two local upload queues + connectivity by
 * [deriveState] (see `HomeStateMapper.kt`).
 */
data class HomeState(
    val destination: HomeDestination? = null,
    val greetingName: String? = null,          // null -> generic "Hola"
    val roleLabel: String? = null,
    val isOnline: Boolean = true,
    val sync: SyncStatusUi = SyncStatusUi.AllSynced(lastSyncedLabel = null),
    val todayMeasurements: Int = 0,
    val todaySubjects: Int = 0,
    val todayAlerts: Int = 0,
    val recent: List<RecentActivityUi> = emptyList(),
    val currentEstablishment: EstablishmentWithStatus? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val showEstablishmentPicker: Boolean = false,
    // Defaults false so the survey entry points are hidden until profile load resolves —
    // consistent with "Home never blocks, never over-shows" (ADR-015's spirit).
    val canRespondToSurveys: Boolean = false,
)

/** The single derived state the sync-status card renders. Precedence lives in [deriveState]. */
sealed interface SyncStatusUi {
    data class AllSynced(val lastSyncedLabel: String?) : SyncStatusUi   // online & clean
    data object OfflineClean : SyncStatusUi                             // offline & nothing queued
    data class Syncing(val count: Int) : SyncStatusUi                   // any row Syncing
    data class PendingOnline(val count: Int) : SyncStatusUi             // pending & online
    data class PendingOffline(val count: Int) : SyncStatusUi           // pending & offline
    data class Failed(val failedCount: Int, val pendingCount: Int) : SyncStatusUi
}

/** One recent-activity row (a queued subject or measurement), ready to render in an `AlListRow`. */
data class RecentActivityUi(
    val id: String,
    val kind: Kind,                 // for the leading icon
    val title: String,              // "Nombre Apellido"
    val subtitle: String?,          // "DNI 12.345.678" (subject) or null
    val value: String?,             // "IMC 24,1" (measurement) or null
    val badgeText: String?,         // alert label or sync label
    val badgeTone: AlBadgeTone,
    val createdAt: Long,
) {
    enum class Kind { Subject, Measurement, SurveyResponse }
}
