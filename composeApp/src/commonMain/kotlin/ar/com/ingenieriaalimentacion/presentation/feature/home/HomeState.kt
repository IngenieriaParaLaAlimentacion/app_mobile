package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.auth.canEditCharacterization
import ar.com.ingenieriaalimentacion.domain.model.auth.establishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.auth.requiresApprovedEstablishment
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
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
    val todaySurveyResponses: Int = 0,
    val recent: List<RecentActivityUi> = emptyList(),
    val currentEstablishment: EstablishmentWithStatus? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val showEstablishmentPicker: Boolean = false,
    /** Backend role once the profile loads; null while unknown (offline / not loaded yet). */
    val role: String? = null,
) {
    /**
     * Admins and analysts act as field workers only inside an approved establishment, so their
     * quick actions wait for one to be selected. Field workers (and an unknown role) keep the
     * current flow; the backend still enforces the link either way.
     */
    val quickActionsEnabled: Boolean
        get() = role == null || !requiresApprovedEstablishment(role) || currentEstablishment != null

    /**
     * "Pendiente de ficha" under the establishment name, for roles that can complete it. Hidden
     * while the role is unknown, so an analyst never gets a shortcut that ends in a 403.
     */
    val showsCharacterizationPending: Boolean
        get() = currentEstablishment?.characterizationStatus == CharacterizationStatus.Pending &&
            role?.let(::canEditCharacterization) == true

    /** Why the quick actions are disabled, or null when they are enabled. */
    val quickActionsHint: String?
        get() = when {
            quickActionsEnabled -> null
            establishmentOptions.isNotEmpty() -> "Elegí un establecimiento aprobado para registrar datos."
            establishmentLinkMode(role.orEmpty()) == EstablishmentLinkMode.Subscribe ->
                "Suscribite a un establecimiento desde Perfil › Mis establecimientos para registrar datos."
            else ->
                "Solicitá acceso a un establecimiento desde Perfil › Mis establecimientos para registrar datos."
        }
}

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
    /** Ingreso and salida are separate kinds, not one `Food` kind: this enum is the only carrier
     *  for the row's leading icon, and direction is the row's headline fact. */
    enum class Kind { Subject, Measurement, SurveyResponse, FoodIntake, FoodOutflow }
}
