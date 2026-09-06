package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.activity.ActivityApi
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.data.activity.mapper.ParsedActivityItem
import ar.com.ingenieriaalimentacion.data.activity.mapper.toParsedEntry
import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.mapper.toHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Union-reads the local subject queue with `/me/activity` history. Mirrors
 * [SubjectQueryRepositoryImpl]'s shape, with the dedup direction flipped (ADR-009: local wins,
 * since queue rows are already fully denormalized and provably the same row as their server copy).
 */
class SubjectHistoryRepositoryImpl(
    private val api: ActivityApi,
    private val cache: ActivityLocalStore,
    private val queue: SubjectLocalStore,
    private val json: Json,
) : SubjectHistoryRepository {

    override fun observeLocalMerged(): Flow<List<SubjectHistoryEntry>> =
        combine(queue.observeAll(), cache.observe(ActivityItemType.SUBJECT)) { queued, cached ->
            merge(queued.map { it.toHistoryEntry() }, cached.map { it.toEntry() })
        }

    override suspend fun loadPage(page: Int, pageSize: Int): Result<SubjectHistoryPage> {
        val remote = runCatching { api.list(page = page, pageSize = pageSize, type = "subject") }
        return remote.fold(
            onSuccess = { response ->
                val items = response.items.mapNotNull {
                    (it.toParsedEntry() as? ParsedActivityItem.SubjectEntry)?.entry
                }
                cache.upsertPage(ActivityItemType.SUBJECT, items.map { it.toCachedRow() })
                if (page != 1) {
                    Result.success(SubjectHistoryPage(items, response.page, response.totalPages))
                } else {
                    val queued = queue.all().map { it.toHistoryEntry() }
                    Result.success(SubjectHistoryPage(merge(queued, items), response.page, response.totalPages))
                }
            },
            onFailure = { error ->
                // Offline: serve queue ∪ cache as a single self-contained page (page 1) or the
                // requested cached page (page > 1) instead of failing.
                val fallback = if (page == 1) {
                    merge(queue.all().map { it.toHistoryEntry() }, cachedPage(1, pageSize))
                } else {
                    cachedPage(page, pageSize)
                }
                if (fallback.isNotEmpty()) Result.success(SubjectHistoryPage(fallback, page, page))
                else Result.failure(error)
            },
        )
    }

    /**
     * Local wins on id collision (ADR-009): queue rows first, remote/cache fills the rest.
     *
     * Ordered by [SubjectHistoryEntry.createdAt] — registration time, which for a remote row is the
     * feed's `occurred_at` (the SUBJECT_CREATED event). Not `updatedAt`: that is a local
     * queue-bookkeeping stamp, so a days-old row retried today would jump to the top of the list
     * while the History screen's day header still dated it to when it was registered.
     */
    private fun merge(local: List<SubjectHistoryEntry>, remote: List<SubjectHistoryEntry>): List<SubjectHistoryEntry> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        return (local + remote.filterNot { it.id in localIds }).sortedByDescending { it.createdAt }
    }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<SubjectHistoryEntry> =
        cache.getPage(ActivityItemType.SUBJECT, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toEntry() }

    private fun SubjectHistoryEntry.toCachedRow(): CachedActivityRow =
        CachedActivityRow(id = id, occurredAtMillis = createdAt, payloadJson = json.encodeToString(this))

    private fun CachedActivityRow.toEntry(): SubjectHistoryEntry = json.decodeFromString(payloadJson)
}
