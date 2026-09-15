package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodStockRepository

/** Scripted-page-queue idiom, copied from [FakeFoodMovementHistoryRepository]. */
class FakeFoodStockRepository : FoodStockRepository {

    /** Consumed in order; the last entry is reused once the list runs out. */
    var pageResults: List<Result<FoodStockPage>> = listOf(
        Result.success(FoodStockPage(items = emptyList(), hasMore = false)),
    )

    /** Every request, in call order — establishment, filters and page, so a test can assert that a
     *  filter change really did reset to page 1. */
    val requests = mutableListOf<Triple<String, FoodStockFilters, Int>>()

    /** Page sizes, in call order. */
    val requestedPageSizes = mutableListOf<Int>()

    override suspend fun getStockPage(
        establishmentId: String,
        filters: FoodStockFilters,
        page: Int,
        pageSize: Int,
    ): Result<FoodStockPage> {
        requests += Triple(establishmentId, filters, page)
        requestedPageSizes += pageSize
        val index = (requests.size - 1).coerceAtMost(pageResults.lastIndex)
        return pageResults[index]
    }
}
