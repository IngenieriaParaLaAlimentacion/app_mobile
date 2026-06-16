package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.datetime.LocalDate

/** Max sync attempts before a transient-failure row dead-letters to [SyncState.Failed]. */
const val MAX_SYNC_ATTEMPTS = 5

/**
 * A locally-queued subject plus its sync metadata. The [localId] is a v4 UUID that doubles as the
 * SQLDelight primary key AND the idempotency `id` sent to the backend.
 */
data class SubjectUpload(
    val localId: String,
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val isRural: Boolean,
    val isIndigenous: Boolean,
    val syncState: SyncState,
    val canonicalId: String?,
    val errorMessage: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Backoff eligibility (ported from extra `MessageScheduler.isEligibleAt`). Fresh rows sync
 * immediately; retrying rows wait an exponential window (2s,4s,8s,16s,30s-cap) plus deterministic
 * per-row jitter (`createdAt % 1000`, no RNG, stable across reads).
 */
fun SubjectUpload.isEligibleAt(nowMs: Long): Boolean {
    if (attemptCount == 0) return true
    val backoff = minOf(30_000L, 1_000L shl attemptCount) + (createdAt % 1_000L)
    return nowMs >= updatedAt + backoff
}
