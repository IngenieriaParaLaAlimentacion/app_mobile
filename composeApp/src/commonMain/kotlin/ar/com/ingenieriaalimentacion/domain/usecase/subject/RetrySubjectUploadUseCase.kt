package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

class RetrySubjectUploadUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.retry(localId)
}
