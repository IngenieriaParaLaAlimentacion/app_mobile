package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository

class FakeSubjectQueryRepository : SubjectQueryRepository {
    /** Page number -> page returned for that page. */
    var pages: Map<Int, SubjectPage> = emptyMap()
    var error: Throwable? = null
    var callCount: Int = 0

    /** Search terms forwarded to the repository, in call order — lets tests assert debounced queries. */
    val searches = mutableListOf<String?>()
    val lastSearch: String? get() = searches.lastOrNull()

    override suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage> {
        callCount++
        searches += query
        error?.let { return Result.failure(it) }
        return Result.success(pages[page] ?: SubjectPage(items = emptyList(), page = page, totalPages = page))
    }
}
