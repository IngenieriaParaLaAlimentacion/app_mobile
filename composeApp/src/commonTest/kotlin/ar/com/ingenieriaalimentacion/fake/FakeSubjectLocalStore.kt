package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SubjectLocalStore] that mirrors the SQL semantics of [DefaultSubjectLocalStore], so the
 * sync engine's state transitions can be unit-tested without a database. Shares the repo's [now].
 */
class FakeSubjectLocalStore(private val now: () -> Long = { 0L }) : SubjectLocalStore {

    private val rows = MutableStateFlow<List<SubjectUpload>>(emptyList())

    fun current(localId: String): SubjectUpload? = rows.value.firstOrNull { it.localId == localId }

    /** Synchronous snapshot for assertions (distinct from the suspend interface [all]). */
    fun snapshot(): List<SubjectUpload> = rows.value

    private fun mutate(localId: String, transform: (SubjectUpload) -> SubjectUpload) {
        rows.value = rows.value.map { if (it.localId == localId) transform(it) else it }
    }

    override fun observeAll(): Flow<List<SubjectUpload>> = rows

    override suspend fun all(): List<SubjectUpload> = rows.value

    override suspend fun find(localId: String): SubjectUpload? = current(localId)

    override suspend fun insert(upload: SubjectUpload) {
        rows.value = rows.value + upload
    }

    override suspend fun syncable(): List<SubjectUpload> =
        rows.value.filter { it.syncState == SyncState.Pending }.sortedBy { it.createdAt }

    override suspend fun markSyncing(localId: String) =
        mutate(localId) { it.copy(syncState = SyncState.Syncing, updatedAt = now()) }

    override suspend fun markSynced(localId: String, state: SyncState, canonicalId: String) =
        mutate(localId) {
            it.copy(syncState = state, canonicalId = canonicalId, errorMessage = null, updatedAt = now())
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
            it.copy(syncState = SyncState.Pending, errorMessage = null, attemptCount = 0, updatedAt = now())
        }

    override suspend fun delete(localId: String) {
        rows.value = rows.value.filterNot { it.localId == localId }
    }

    override suspend fun resetStuckSyncing() {
        rows.value = rows.value.map {
            if (it.syncState == SyncState.Syncing) it.copy(syncState = SyncState.Pending, updatedAt = now()) else it
        }
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long) {
        rows.value = rows.value.filterNot {
            it.syncState == SyncState.Synced && it.updatedAt < cutoff
        }
    }
}
