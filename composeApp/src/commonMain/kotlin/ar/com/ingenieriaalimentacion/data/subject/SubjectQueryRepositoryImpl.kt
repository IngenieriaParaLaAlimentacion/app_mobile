package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toSummary
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository

/**
 * Unions the remote `/subjects` page with locally-queued subjects so an offline-created subject is
 * selectable in the picker. Only page 1 carries the local rows (the queue is small and unpaged);
 * later pages pass the remote result through. MERGED/FAILED rows are hidden — a MERGED subject is
 * already represented by its canonical remote row, and a FAILED one never reached the server.
 */
class SubjectQueryRepositoryImpl(
    private val api: SubjectApi,
    private val local: SubjectLocalStore,
) : SubjectQueryRepository {

    override suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage> {
        val remote = runCatching { api.list(page = page, pageSize = pageSize, search = query).toDomain() }

        // Local rows only join page 1; deeper pages are purely remote.
        if (page != 1) return remote

        val localSummaries = localSummaries(query)

        return remote.fold(
            onSuccess = { remotePage ->
                // Prepend local-only subjects; remote wins on any id collision (the synced row).
                val remoteIds = remotePage.items.mapTo(mutableSetOf()) { it.id }
                val merged = localSummaries.filterNot { it.id in remoteIds } + remotePage.items
                Result.success(remotePage.copy(items = merged))
            },
            onFailure = { error ->
                // Offline: serve the local queue as a single self-contained page instead of failing.
                if (localSummaries.isNotEmpty()) {
                    Result.success(SubjectPage(items = localSummaries, page = 1, totalPages = 1))
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    /** Picker-visible local rows (PENDING/SYNCING/SYNCED), filtered client-side by [query]. */
    private suspend fun localSummaries(query: String?): List<SubjectSummary> =
        local.all()
            .filter { it.syncState in PICKER_VISIBLE_STATES }
            .filter { it.matches(query) }
            .map { it.toSummary() }

    private fun SubjectUpload.matches(query: String?): Boolean {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) ||
            lastName.contains(q, ignoreCase = true) ||
            dni.toString().contains(q)
    }

    private companion object {
        val PICKER_VISIBLE_STATES = setOf(SyncState.Pending, SyncState.Syncing, SyncState.Synced)
    }
}
