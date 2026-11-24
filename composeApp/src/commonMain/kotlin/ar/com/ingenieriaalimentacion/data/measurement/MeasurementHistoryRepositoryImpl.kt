package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.activity.ActivityApi
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.data.activity.mapper.ParsedActivityItem
import ar.com.ingenieriaalimentacion.data.activity.mapper.toParsedEntry
import ar.com.ingenieriaalimentacion.data.measurement.local.MeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Merges the upload queue with authoritative measurement results from the owner's activity feed.
 */
class MeasurementHistoryRepositoryImpl(
    private val api: ActivityApi,
    private val cache: ActivityLocalStore,
    private val queue: MeasurementLocalStore,
    private val json: Json,
    private val now: () -> Long,
) : MeasurementHistoryRepository {
    private val pageLock = Mutex()

    override fun observeLocalMerged(): Flow<List<MeasurementHistoryEntry>> =
        combine(queue.observeAll(), cache.observe(ActivityItemType.MEASUREMENT)) { queued, cached ->
            merge(queued.map { it.toHistoryEntry() }, cached.map { it.toEntry() })
        }

    override suspend fun loadPage(page: Int, pageSize: Int): Result<MeasurementHistoryPage> = pageLock.withLock {
        val observedAt = now()
        val remote = runCatching { api.list(page = page, pageSize = pageSize, type = "measurement") }
        remote.fold(
            onSuccess = { response ->
                val items = response.items.mapNotNull {
                    (it.toParsedEntry() as? ParsedActivityItem.MeasurementEntry)?.entry
                        ?.copy(serverObservedAtMillis = observedAt)
                }
                cache.upsertPage(ActivityItemType.MEASUREMENT, items.map { it.toCachedRow() })
                if (page != 1) {
                    Result.success(MeasurementHistoryPage(items, response.page, response.totalPages))
                } else {
                    Result.success(MeasurementHistoryPage(merge(queuedEntries(), items), response.page, response.totalPages))
                }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                // Offline: serve queue ∪ cache as a single self-contained page (page 1) or the
                // requested cached page (page > 1) instead of failing.
                val fallback = if (page == 1) {
                    merge(queuedEntries(), cachedPage(1, pageSize))
                } else {
                    cachedPage(page, pageSize)
                }
                if (fallback.isNotEmpty()) Result.success(MeasurementHistoryPage(fallback, page, page))
                else Result.failure(error)
            },
        )
    }

    /**
     * Active uploads keep their queue state. After sync, a feed request that started later carries
     * the authoritative clinical values. Keep the queue timestamps for local sync bookkeeping.
     */
    private fun merge(
        local: List<MeasurementHistoryEntry>,
        remote: List<MeasurementHistoryEntry>,
    ): List<MeasurementHistoryEntry> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        val remoteById = remote.associateBy { it.id }
        val merged = local.map { queued ->
            val server = remoteById[queued.id]
            val observedAt = server?.serverObservedAtMillis
            if (server != null && observedAt != null && queued.syncState == SyncState.Synced &&
                observedAt > queued.updatedAt
            ) {
                server.copy(isLocal = true, createdAt = queued.createdAt, updatedAt = queued.updatedAt)
            } else {
                queued
            }
        }
        return (merged + remote.filterNot { it.id in localIds }).sortedByDescending { it.measuredAtMillis() }
    }

    /** 0 for an unparseable timestamp, which sorts the row last — where "Sin fecha" renders. */
    private fun MeasurementHistoryEntry.measuredAtMillis(): Long =
        runCatching { Instant.parse(measuredAt).toEpochMilliseconds() }.getOrDefault(0L)

    private suspend fun queuedEntries(): List<MeasurementHistoryEntry> = queue.all().map { it.toHistoryEntry() }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<MeasurementHistoryEntry> =
        cache.getPage(ActivityItemType.MEASUREMENT, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toEntry() }

    private fun MeasurementHistoryEntry.toCachedRow(): CachedActivityRow =
        CachedActivityRow(id = id, occurredAtMillis = measuredAtMillis(), payloadJson = json.encodeToString(this))

    private fun CachedActivityRow.toEntry(): MeasurementHistoryEntry = json.decodeFromString(payloadJson)
}
