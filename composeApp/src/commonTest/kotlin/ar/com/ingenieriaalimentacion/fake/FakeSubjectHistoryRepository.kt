package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSubjectHistoryRepository : SubjectHistoryRepository {
    val merged = MutableStateFlow<List<SubjectHistoryEntry>>(emptyList())

    /** Default response for any page not explicitly stubbed via [pageResultsByPage]. */
    var pageResult: Result<SubjectHistoryPage> = Result.success(SubjectHistoryPage(emptyList(), 1, 1))

    /** Per-page overrides, so page-1 (init refresh) and page-2+ (LoadMore) can differ in tests. */
    val pageResultsByPage = mutableMapOf<Int, Result<SubjectHistoryPage>>()
    val pageRequests = mutableListOf<Int>()

    override fun observeLocalMerged(): Flow<List<SubjectHistoryEntry>> = merged

    override suspend fun loadPage(page: Int, pageSize: Int): Result<SubjectHistoryPage> {
        pageRequests += page
        return pageResultsByPage[page] ?: pageResult
    }
}
