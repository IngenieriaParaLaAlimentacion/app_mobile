package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import kotlinx.coroutines.flow.Flow

/** Observes the merged local measurement ledger (queue ∪ cache), for Home and History's floor. */
class ObserveMeasurementsUseCase(private val repository: MeasurementHistoryRepository) {
    operator fun invoke(): Flow<List<MeasurementHistoryEntry>> = repository.observeLocalMerged()
}
