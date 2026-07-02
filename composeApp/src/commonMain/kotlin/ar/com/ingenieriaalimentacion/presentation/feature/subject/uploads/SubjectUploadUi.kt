package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** Display model for one upload row: domain data plus a localized badge and allowed actions. */
data class SubjectUploadUi(
    val localId: String,
    val title: String,
    val subtitle: String,
    val badgeLabel: String,
    val badgeTone: AlBadgeTone,
    val showSpinner: Boolean,
    val canRetry: Boolean,
    val canDelete: Boolean,
)

internal fun SubjectHistoryEntry.toUi(): SubjectUploadUi {
    val (label, tone) = when (syncState) {
        SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
        SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
        SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
        SyncState.Merged -> "Ya existía" to AlBadgeTone.Warning
        SyncState.Failed -> "Error" to AlBadgeTone.Danger
    }
    val detail = buildString {
        append("DNI ")
        append(dni)
        when {
            syncState == SyncState.Failed && !errorMessage.isNullOrBlank() -> {
                append(" · ")
                append(errorMessage)
            }
            syncState == SyncState.Merged && canonicalId != null -> {
                append(" · id ")
                append(canonicalId)
            }
        }
    }
    return SubjectUploadUi(
        localId = id,
        title = "$name $lastName",
        subtitle = detail,
        badgeLabel = label,
        badgeTone = tone,
        showSpinner = syncState == SyncState.Syncing,
        canRetry = isLocal && syncState == SyncState.Failed,
        canDelete = isLocal && (
            syncState == SyncState.Pending ||
                syncState == SyncState.Failed ||
                syncState == SyncState.Merged
            ),
    )
}
