package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository

class GetFoodMovementHistoryPageUseCase(private val repository: FoodMovementHistoryRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int): Result<FoodMovementHistoryPage> =
        repository.getPage(page, pageSize)
}
