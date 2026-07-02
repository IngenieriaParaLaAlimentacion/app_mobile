package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.measurement.local.MeasurementLocalStore
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [MeasurementLocalStore] mirroring the SQL semantics of [DefaultMeasurementLocalStore], so
 * the sync engine's state transitions can be unit-tested without a database. Shares the repo's [now].
 */
class FakeMeasurementLocalStore(private val now: () -> Long = { 0L }) : MeasurementLocalStore {

    private val rows = MutableStateFlow<List<MeasurementUpload>>(emptyList())

    fun current(localId: String): MeasurementUpload? = rows.value.firstOrNull { it.localId == localId }

    private fun mutate(localId: String, transform: (MeasurementUpload) -> MeasurementUpload) {
        rows.value = rows.value.map { if (it.localId == localId) transform(it) else it }
    }

    override fun observeAll(): Flow<List<MeasurementUpload>> = rows

    override suspend fun all(): List<MeasurementUpload> = rows.value

    override suspend fun insert(upload: MeasurementUpload) {
        rows.value = rows.value + upload
    }

    override suspend fun syncable(): List<MeasurementUpload> =
        rows.value.filter { it.syncState == SyncState.Pending }.sortedBy { it.createdAt }

    override suspend fun markSyncing(localId: String) =
        mutate(localId) { it.copy(syncState = SyncState.Syncing, updatedAt = now()) }

    override suspend fun markSynced(localId: String, bmi: Double?, alertsJson: String?) =
        mutate(localId) {
            it.copy(
                syncState = SyncState.Synced,
                bmi = bmi,
                // Store the raw JSON in errorMessage-free fashion; the test asserts via alertsJson arg.
                errorMessage = null,
                updatedAt = now(),
                alerts = it.alerts, // alerts decoding is exercised by the local-store/mapper tests
            )
        }.also { lastSyncedAlertsJson = alertsJson }

    /** Captures the most recent serialized alerts handed to [markSynced] for assertions. */
    var lastSyncedAlertsJson: String? = null

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
        rows.value = rows.value.filterNot { it.syncState == SyncState.Synced && it.updatedAt < cutoff }
    }
}
