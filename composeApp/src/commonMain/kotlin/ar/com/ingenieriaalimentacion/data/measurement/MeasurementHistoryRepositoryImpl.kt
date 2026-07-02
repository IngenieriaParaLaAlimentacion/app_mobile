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
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Union-reads the local measurement queue with `/me/activity` history. Mirrors
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectHistoryRepositoryImpl] exactly, on the
 * measurement queue/type instead.
 */
class MeasurementHistoryRepositoryImpl(
    private val api: ActivityApi,
    private val cache: ActivityLocalStore,
    private val queue: MeasurementLocalStore,
    private val json: Json,
) : MeasurementHistoryRepository {

    override fun observeLocalMerged(): Flow<List<MeasurementHistoryEntry>> =
        combine(queue.observeAll(), cache.observe(ActivityItemType.MEASUREMENT)) { queued, cached ->
            merge(queued.map { it.toHistoryEntry() }, cached.map { it.toEntry() })
        }

    override suspend fun loadPage(page: Int, pageSize: Int): Result<MeasurementHistoryPage> {
        val remote = runCatching { api.list(page = page, pageSize = pageSize, type = "measurement") }
        return remote.fold(
            onSuccess = { response ->
                val items = response.items.mapNotNull {
                    (it.toParsedEntry() as? ParsedActivityItem.MeasurementEntry)?.entry
                }
                cache.upsertPage(ActivityItemType.MEASUREMENT, items.map { it.toCachedRow() })
                if (page != 1) {
                    Result.success(MeasurementHistoryPage(items, response.page, response.totalPages))
                } else {
                    Result.success(MeasurementHistoryPage(merge(queuedEntries(), items), response.page, response.totalPages))
                }
            },
            onFailure = { error ->
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

    /** Local wins on id collision (ADR-009): queue rows first, remote/cache fills the rest. */
    private fun merge(
        local: List<MeasurementHistoryEntry>,
        remote: List<MeasurementHistoryEntry>,
    ): List<MeasurementHistoryEntry> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        return (local + remote.filterNot { it.id in localIds }).sortedByDescending { it.updatedAt }
    }

    private suspend fun queuedEntries(): List<MeasurementHistoryEntry> = queue.all().map { it.toHistoryEntry() }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<MeasurementHistoryEntry> =
        cache.getPage(ActivityItemType.MEASUREMENT, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toEntry() }

    private fun MeasurementHistoryEntry.toCachedRow(): CachedActivityRow =
        CachedActivityRow(id = id, occurredAtMillis = updatedAt, payloadJson = json.encodeToString(this))

    private fun CachedActivityRow.toEntry(): MeasurementHistoryEntry = json.decodeFromString(payloadJson)
}
