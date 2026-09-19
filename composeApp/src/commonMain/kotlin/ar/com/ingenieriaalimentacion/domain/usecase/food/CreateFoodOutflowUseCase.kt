package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow
import ar.com.ingenieriaalimentacion.domain.repository.FoodOutflowRepository

class CreateFoodOutflowUseCase(private val repository: FoodOutflowRepository) {
    suspend operator fun invoke(outflow: NewFoodOutflow): Result<Unit> =
        repository.createOutflow(outflow)
}
