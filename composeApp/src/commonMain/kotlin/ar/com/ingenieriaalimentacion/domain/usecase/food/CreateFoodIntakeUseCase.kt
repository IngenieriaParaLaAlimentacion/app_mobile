package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.repository.FoodIntakeRepository

class CreateFoodIntakeUseCase(private val repository: FoodIntakeRepository) {
    suspend operator fun invoke(intake: NewFoodIntake): Result<Unit> = repository.createIntake(intake)
}
