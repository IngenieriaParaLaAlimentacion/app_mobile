package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.serialization.Serializable

/**
 * One row in the subject ledger (History / Home) — either a still-queued [SubjectUpload] or a
 * reconciled `/me/activity` item. Deliberately smaller than [SubjectUpload]: a remote item never
 * carries birthDate/isRural/isIndigenous (not part of the activity payload, and never displayed
 * anywhere), so this type only carries what's ever rendered. `@Serializable` so it round-trips
 * through the `CachedActivityItem.payloadJson` column.
 */
@Serializable
data class SubjectHistoryEntry(
    val id: String,
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val syncState: SyncState,     // always Synced for a remote-sourced entry
    val canonicalId: String?,     // MERGED local rows only
    val errorMessage: String?,    // FAILED local rows only
    val isLocal: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SubjectHistoryPage(
    val items: List<SubjectHistoryEntry>,
    val page: Int,
    val totalPages: Int,
)
