package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository

class GetSubjectsUseCase(private val repository: SubjectQueryRepository) {
    suspend operator fun invoke(query: String?, page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<SubjectPage> =
        repository.searchSubjects(query, page, pageSize)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
