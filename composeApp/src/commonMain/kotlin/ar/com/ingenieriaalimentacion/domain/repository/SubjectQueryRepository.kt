package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage

interface SubjectQueryRepository {
    suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage>
}
