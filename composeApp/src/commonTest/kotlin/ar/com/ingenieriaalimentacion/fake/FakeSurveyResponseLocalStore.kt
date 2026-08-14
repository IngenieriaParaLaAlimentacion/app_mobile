package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SurveyResponseLocalStore] that mirrors the SQL semantics of
 * [ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyResponseLocalStore], so the sync
 * engine's state transitions can be unit-tested without a database.
 */
class FakeSurveyResponseLocalStore(private val now: () -> Long = { 0L }) : SurveyResponseLocalStore {

    private val rows = MutableStateFlow<List<SurveyResponseUpload>>(emptyList())

    fun current(localId: String): SurveyResponseUpload? = rows.value.firstOrNull { it.localId == localId }

    fun snapshot(): List<SurveyResponseUpload> = rows.value

    private fun mutate(localId: String, transform: (SurveyResponseUpload) -> SurveyResponseUpload) {
        rows.value = rows.value.map { if (it.localId == localId) transform(it) else it }
    }

    override fun observeAll(): Flow<List<SurveyResponseUpload>> = rows

    override suspend fun all(): List<SurveyResponseUpload> = rows.value

    override suspend fun find(localId: String): SurveyResponseUpload? = current(localId)

    override suspend fun insert(upload: SurveyResponseUpload) {
        rows.value = rows.value + upload
    }

    override suspend fun syncable(): List<SurveyResponseUpload> =
        rows.value.filter { it.syncState == SyncState.Pending }.sortedBy { it.createdAt }

    override suspend fun markSyncing(localId: String) =
        mutate(localId) { it.copy(syncState = SyncState.Syncing, updatedAt = now()) }

    override suspend fun markSynced(localId: String, canonicalId: String) =
        mutate(localId) {
            it.copy(syncState = SyncState.Synced, canonicalId = canonicalId, errorMessage = null, updatedAt = now())
        }

    override suspend fun markFailed(localId: String, reason: String) =
        mutate(localId) {
            it.copy(
                syncState = SyncState.Failed,
                errorMessage = reason,
                attemptCount = it.attemptCount + 1,
                updatedAt = now(),
            )
        }

    override suspend fun markConflict(localId: String, conflict: ResponseConflict) =
        mutate(localId) {
            it.copy(
                syncState = SyncState.Failed,
                errorMessage = null,
                conflict = conflict,
                attemptCount = it.attemptCount + 1,
                updatedAt = now(),
            )
        }

    override suspend fun delete(localId: String) {
        rows.value = rows.value.filterNot { it.localId == localId }
    }

    override suspend fun bumpRetry(localId: String, reason: String) =
        mutate(localId) {
            it.copy(
                syncState = SyncState.Pending,
                errorMessage = reason,
                attemptCount = it.attemptCount + 1,
                updatedAt = now(),
            )
        }

    override suspend fun resetForRetry(localId: String) =
        mutate(localId) {
            it.copy(
                syncState = SyncState.Pending,
                errorMessage = null,
                conflict = null,
                attemptCount = 0,
                updatedAt = now(),
            )
        }

    override suspend fun resetStuckSyncing() {
        rows.value = rows.value.map {
            if (it.syncState == SyncState.Syncing) it.copy(syncState = SyncState.Pending, updatedAt = now()) else it
        }
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long) {
        rows.value = rows.value.filterNot { it.syncState == SyncState.Failed && it.updatedAt < cutoff }
    }
}
