package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

class DeleteSubjectUploadUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.delete(localId)
}
