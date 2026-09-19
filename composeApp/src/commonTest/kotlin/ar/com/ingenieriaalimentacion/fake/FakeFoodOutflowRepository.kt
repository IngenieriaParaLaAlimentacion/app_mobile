package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow
import ar.com.ingenieriaalimentacion.domain.repository.FoodOutflowRepository

class FakeFoodOutflowRepository : FoodOutflowRepository {
    var result: Result<Unit> = Result.success(Unit)
    val created = mutableListOf<NewFoodOutflow>()

    override suspend fun createOutflow(outflow: NewFoodOutflow): Result<Unit> {
        created += outflow
        return result
    }
}
