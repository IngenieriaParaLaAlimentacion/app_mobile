package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.repository.FoodIntakeRepository

class FakeFoodIntakeRepository : FoodIntakeRepository {

    var result: Result<Unit> = Result.success(Unit)

    /** Every intake handed to the repository, in call order — the id lifecycle is asserted here. */
    val created = mutableListOf<NewFoodIntake>()

    override suspend fun createIntake(intake: NewFoodIntake): Result<Unit> {
        created += intake
        return result
    }
}
