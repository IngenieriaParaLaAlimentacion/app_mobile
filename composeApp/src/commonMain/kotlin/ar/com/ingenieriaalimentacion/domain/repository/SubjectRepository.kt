package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject

interface SubjectRepository {
    suspend fun create(newSubject: NewSubject): Result<Subject>
}
