package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow

/** Observes the local measurement queue (for the history, Historia 3). Built now, consumed later. */
class ObserveMeasurementsUseCase(private val repository: MeasurementRepository) {
    operator fun invoke(): Flow<List<MeasurementUpload>> = repository.observeUploads()
}
