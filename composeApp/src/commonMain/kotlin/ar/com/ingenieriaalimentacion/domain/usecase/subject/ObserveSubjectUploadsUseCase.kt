package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveSubjectUploadsUseCase(private val repository: SubjectHistoryRepository) {
    operator fun invoke(): Flow<List<SubjectHistoryEntry>> = repository.observeLocalMerged()
}
