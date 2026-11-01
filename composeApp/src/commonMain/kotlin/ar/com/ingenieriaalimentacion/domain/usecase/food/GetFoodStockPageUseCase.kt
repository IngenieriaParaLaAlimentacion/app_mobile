package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodStockRepository

class GetFoodStockPageUseCase(private val repository: FoodStockRepository) {
    suspend operator fun invoke(
        establishmentId: String,
        filters: FoodStockFilters,
        page: Int,
        pageSize: Int,
    ): Result<FoodStockPage> = repository.getStockPage(establishmentId, filters, page, pageSize)
}
