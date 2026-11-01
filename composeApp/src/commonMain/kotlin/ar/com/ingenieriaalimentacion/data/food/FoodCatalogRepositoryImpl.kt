package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository
import kotlinx.coroutines.CancellationException

class FoodCatalogRepositoryImpl(private val api: FoodCatalogApi) : FoodCatalogRepository {
    override suspend fun getFood(id: String): Result<Food> = try {
        Result.success(api.getFood(id).toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
    override suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult> =
        runCatching { api.searchFoods(search, pageSize).toDomain() }

    override suspend fun getCategories(): Result<List<FoodCategory>> =
        runCatching { api.listCategories().map { it.toDomain() } }
}
