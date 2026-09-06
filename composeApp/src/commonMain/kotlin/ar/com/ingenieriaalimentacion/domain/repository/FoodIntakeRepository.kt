package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake

/**
 * Live `POST /food-intakes` — the first write in the app with no local queue behind it. A failed
 * [Result] carries a [ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeFailure] with a
 * typed [ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeError].
 */
interface FoodIntakeRepository {
    suspend fun createIntake(intake: NewFoodIntake): Result<Unit>
}
