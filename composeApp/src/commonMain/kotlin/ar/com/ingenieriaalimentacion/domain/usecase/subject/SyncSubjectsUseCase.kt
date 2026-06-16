package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

class SyncSubjectsUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(): SyncSummary = repository.syncPending()
}
