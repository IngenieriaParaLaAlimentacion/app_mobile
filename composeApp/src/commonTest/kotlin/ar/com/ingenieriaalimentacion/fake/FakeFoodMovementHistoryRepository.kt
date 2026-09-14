package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFoodMovementHistoryRepository : FoodMovementHistoryRepository {
    val localMerged = MutableStateFlow<List<FoodMovement>>(emptyList())

    /** Consumed in order; the last entry is reused once the list runs out. */
    var pageResults: List<Result<FoodMovementHistoryPage>> = listOf(
        Result.success(FoodMovementHistoryPage(items = emptyList(), hasMore = false)),
    )

    val requestedPages = mutableListOf<Pair<Int, Int>>()

    override fun observeLocalMerged(): Flow<List<FoodMovement>> = localMerged

    override suspend fun getPage(page: Int, pageSize: Int): Result<FoodMovementHistoryPage> {
        requestedPages += page to pageSize
        val index = (requestedPages.size - 1).coerceAtMost(pageResults.lastIndex)
        return pageResults[index]
    }
}
