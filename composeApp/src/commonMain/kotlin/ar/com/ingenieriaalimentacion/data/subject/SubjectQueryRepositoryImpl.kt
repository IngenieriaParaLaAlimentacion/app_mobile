package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository

class SubjectQueryRepositoryImpl(private val api: SubjectApi) : SubjectQueryRepository {
    override suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage> =
        runCatching { api.list(page = page, pageSize = pageSize, search = query).toDomain() }
}
