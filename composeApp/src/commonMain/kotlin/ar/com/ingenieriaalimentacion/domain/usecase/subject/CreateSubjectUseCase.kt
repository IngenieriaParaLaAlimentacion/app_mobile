package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

/**
 * Enqueues a new subject locally as PENDING (offline-first). Returns immediately with the new
 * subject's localId, so a caller mid-measurement can reference it without waiting for the sync.
 */
class CreateSubjectUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(newSubject: NewSubject): Result<String> =
        repository.enqueue(newSubject)
}
