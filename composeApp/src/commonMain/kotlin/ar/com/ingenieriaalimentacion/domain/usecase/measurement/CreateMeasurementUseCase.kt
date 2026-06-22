package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository

/** Enqueues a new measurement locally as PENDING (offline-first). Returns immediately. */
class CreateMeasurementUseCase(private val repository: MeasurementRepository) {
    suspend operator fun invoke(measurement: NewMeasurement): Result<Unit> =
        repository.enqueue(measurement)
}
