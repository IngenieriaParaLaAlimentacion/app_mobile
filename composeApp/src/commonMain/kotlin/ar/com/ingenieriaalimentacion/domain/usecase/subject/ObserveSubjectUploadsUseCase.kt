package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import kotlinx.coroutines.flow.Flow

class ObserveSubjectUploadsUseCase(private val repository: SubjectRepository) {
    operator fun invoke(): Flow<List<SubjectUpload>> = repository.observeUploads()
}
