package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository

class DeleteMeasurementUploadUseCase(private val repository: MeasurementRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.delete(localId)
}
