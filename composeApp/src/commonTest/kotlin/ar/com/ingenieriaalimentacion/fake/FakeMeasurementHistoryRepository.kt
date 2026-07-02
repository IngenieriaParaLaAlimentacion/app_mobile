package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMeasurementHistoryRepository : MeasurementHistoryRepository {
    val merged = MutableStateFlow<List<MeasurementHistoryEntry>>(emptyList())

    /** Default response for any page not explicitly stubbed via [pageResultsByPage]. */
    var pageResult: Result<MeasurementHistoryPage> = Result.success(MeasurementHistoryPage(emptyList(), 1, 1))

    /** Per-page overrides, so page-1 (init refresh) and page-2+ (LoadMore) can differ in tests. */
    val pageResultsByPage = mutableMapOf<Int, Result<MeasurementHistoryPage>>()
    val pageRequests = mutableListOf<Int>()

    override fun observeLocalMerged(): Flow<List<MeasurementHistoryEntry>> = merged

    override suspend fun loadPage(page: Int, pageSize: Int): Result<MeasurementHistoryPage> {
        pageRequests += page
        return pageResultsByPage[page] ?: pageResult
    }
}
