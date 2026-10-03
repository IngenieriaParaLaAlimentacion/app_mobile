package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository

class FakeFoodCatalogRepository : FoodCatalogRepository {

    var foodResult: Result<Food> = Result.failure(NoSuchElementException("food not configured"))
    val foodCalls = mutableListOf<String>()
    override suspend fun getFood(id: String): Result<Food> {
        foodCalls += id
        return foodResult
    }

    var result: Result<FoodSearchResult> = Result.success(FoodSearchResult(emptyList(), 0))

    /** Search terms forwarded to the catalog, in call order. */
    val searchCalls = mutableListOf<String?>()

    override suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult> {
        searchCalls += search
        return result
    }

    var categoriesResult: Result<List<FoodCategory>> = Result.success(emptyList())
    var categoriesCallCount: Int = 0

    override suspend fun getCategories(): Result<List<FoodCategory>> {
        categoriesCallCount++
        return categoriesResult
    }
}
