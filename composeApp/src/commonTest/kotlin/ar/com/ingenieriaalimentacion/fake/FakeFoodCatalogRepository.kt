package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository

class FakeFoodCatalogRepository : FoodCatalogRepository {

    var result: Result<FoodSearchResult> = Result.success(FoodSearchResult(emptyList(), 0))

    /** Search terms forwarded to the catalog, in call order. */
    val searchCalls = mutableListOf<String?>()

    override suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult> {
        searchCalls += search
        return result
    }
}
