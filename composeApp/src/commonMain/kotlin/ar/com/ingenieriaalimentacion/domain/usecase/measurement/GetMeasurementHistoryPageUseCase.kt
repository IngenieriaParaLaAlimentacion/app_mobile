package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository

class GetMeasurementHistoryPageUseCase(private val repository: MeasurementHistoryRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<MeasurementHistoryPage> =
        repository.loadPage(page, pageSize)

    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}
