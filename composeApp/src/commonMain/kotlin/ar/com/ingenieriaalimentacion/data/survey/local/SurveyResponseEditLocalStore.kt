package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.coroutines.flow.Flow

/** Local source of truth for the survey-response edit queue — a second, independent queue from
 *  [SurveyResponseLocalStore], keyed by its own `editId`. The concrete
 *  [DefaultSurveyResponseEditLocalStore] wraps SQLDelight; tests use a fake. */
interface SurveyResponseEditLocalStore {
    /** The latest edit, including a failed edit the response detail can surface. */
    fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?>

    /** Save a new edit, coalescing with an unsent pending edit if one exists. */
    suspend fun enqueue(edit: SurveyResponseEditUpload)

    /** All PENDING rows; backoff gating is applied in Kotlin by the caller. */
    suspend fun syncable(): List<SurveyResponseEditUpload>
    suspend fun awaitingNumberPolicy(): List<SurveyResponseEditUpload>

    /** Atomically claim the exact candidate before sending its immutable request. */
    suspend fun claimForSync(edit: SurveyResponseEditUpload): Boolean

    suspend fun markSynced(editId: String)

    /** Terminal failure: bumps attemptCount and sets a user-facing reason. */
    suspend fun markFailed(editId: String, reason: String)

    /** Transient failure: stay PENDING, count the attempt, restart the backoff window. */
    suspend fun bumpRetry(editId: String, reason: String)

    /** Manual Retry on a dead-letter FAILED row: fresh start (attemptCount → 0). */
    suspend fun resetForRetry(editId: String)

    /** CRASH RECOVERY — un-stick rows left mid-PUT. Call once on SyncCoordinator.start(). */
    suspend fun resetStuckSyncing()

    /** GC terminal (SYNCED or FAILED) rows older than [cutoff] (epoch ms). */
    suspend fun pruneTerminalOlderThan(cutoff: Long)
}
