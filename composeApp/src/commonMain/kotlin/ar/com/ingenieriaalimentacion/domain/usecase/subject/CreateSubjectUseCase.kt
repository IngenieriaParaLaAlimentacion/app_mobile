package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

class CreateSubjectUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(newSubject: NewSubject): Result<Subject> =
        repository.create(newSubject)
}
