package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository

class GetSubjectHistoryPageUseCase(private val repository: SubjectHistoryRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<SubjectHistoryPage> =
        repository.loadPage(page, pageSize)

    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}
