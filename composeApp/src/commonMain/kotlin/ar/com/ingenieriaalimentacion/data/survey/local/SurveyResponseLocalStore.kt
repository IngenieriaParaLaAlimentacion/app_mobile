package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import kotlinx.coroutines.flow.Flow

/**
 * Local source of truth for the survey-response upload queue. The concrete
 * [DefaultSurveyResponseLocalStore] wraps SQLDelight; tests use a fake. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore].
 */
interface SurveyResponseLocalStore {
    fun observeAll(): Flow<List<SurveyResponseUpload>>

    /** One-shot snapshot of every queued row, used by the history union read. */
    suspend fun all(): List<SurveyResponseUpload>

    /** Single queued row by its [localId] — the FK-gate lookup [SurveyResponseEditRepositoryImpl]
     *  uses to resolve a parent response's own queue state. */
    suspend fun find(localId: String): SurveyResponseUpload?

    suspend fun insert(upload: SurveyResponseUpload)

    /** All PENDING rows; backoff gating is applied in Kotlin by the caller. */
    suspend fun syncable(): List<SurveyResponseUpload>
    suspend fun awaitingNumberPolicy(): List<SurveyResponseUpload>

    suspend fun markSyncing(localId: String)
    suspend fun markSynced(localId: String, canonicalId: String)

    /** Terminal failure: bumps attemptCount and sets a user-facing reason. */
    suspend fun markFailed(localId: String, reason: String)

    /** Terminal 409: this survey is already answered for this target. Clears [errorMessage] and
     *  records who answered and when, which makes the row ineligible for every future sweep
     *  (see `SurveyResponseUpload.isEligibleAt`). Only [delete] clears it. */
    suspend fun markConflict(localId: String, conflict: ResponseConflict)

    suspend fun updateResponseNumber(localId: String, responseNumber: Int)

    /** Discard a row for good — the user's *Descartar* action on a conflicted or dead-lettered row. */
    suspend fun delete(localId: String)

    /** Transient failure: stay PENDING, count the attempt, restart the backoff window. */
    suspend fun bumpRetry(localId: String, reason: String)

    /** Manual Retry on a dead-letter FAILED row: fresh start (attemptCount → 0). */
    suspend fun resetForRetry(localId: String)

    /** CRASH RECOVERY — un-stick rows left mid-POST. Call once on SyncCoordinator.start(). */
    suspend fun resetStuckSyncing()

    /** GC FAILED rows older than [cutoff] (epoch ms) — SYNCED rows are exempt, kept as the local
     *  edit-prefill fallback. Call from the periodic cleanup loop. */
    suspend fun pruneTerminalOlderThan(cutoff: Long)
}
