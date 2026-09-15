package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository

/**
 * The 24 SARA 2 food groups, for the stock screen's category filter.
 *
 * This is the **whole catalog**, not the categories present in one establishment's stock — the
 * backend exposes no "categories with stock here" endpoint, and deriving the list from the loaded
 * page would make the filter change as the user scrolls. The cost is that some chips yield an empty
 * page; the screen's empty state is filter-aware so that reads correctly.
 */
class GetFoodCategoriesUseCase(private val repository: FoodCatalogRepository) {
    suspend operator fun invoke(): Result<List<FoodCategory>> = repository.getCategories()
}
