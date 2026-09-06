package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository

class FoodCatalogRepositoryImpl(private val api: FoodCatalogApi) : FoodCatalogRepository {
    override suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult> =
        runCatching { api.searchFoods(search, pageSize).toDomain() }
}
