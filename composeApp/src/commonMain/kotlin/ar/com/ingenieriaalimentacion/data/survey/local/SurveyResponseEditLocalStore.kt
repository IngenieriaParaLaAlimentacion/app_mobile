package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.coroutines.flow.Flow

/** Local source of truth for the survey-response edit queue — a second, independent queue from
 *  [SurveyResponseLocalStore], keyed by its own `editId`. The concrete
 *  [DefaultSurveyResponseEditLocalStore] wraps SQLDelight; tests use a fake. */
interface SurveyResponseEditLocalStore {
    /** The still-unsent edit attempt for [responseId], if any — the coalescing lookup
     *  `SurveyResponseEditRepositoryImpl.enqueueEdit` uses to overwrite in place instead of
     *  minting a new idempotency key. */
    suspend fun findPendingForResponse(responseId: String): SurveyResponseEditUpload?

    /** The most recent edit attempt (any state) for [responseId], reactively — lets the detail
     *  screen surface a dead-lettered (FAILED) edit instead of it silently vanishing. */
    fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?>

    /** Overwrite a not-yet-transmitted edit in place — same `editId`. Refreshes the denormalized
     *  [surveyName]/[establishmentName] too, so a second offline edit doesn't leave them stale. */
    suspend fun updatePending(
        editId: String,
        surveyName: String,
        establishmentName: String,
        answersJson: String,
        eventAt: String,
        responseNumber: Int?,
        now: Long,
    )

    suspend fun insert(edit: SurveyResponseEditUpload)

    /** All PENDING rows; backoff gating is applied in Kotlin by the caller. */
    suspend fun syncable(): List<SurveyResponseEditUpload>
    suspend fun awaitingNumberPolicy(): List<SurveyResponseEditUpload>

    suspend fun markSyncing(editId: String)
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
