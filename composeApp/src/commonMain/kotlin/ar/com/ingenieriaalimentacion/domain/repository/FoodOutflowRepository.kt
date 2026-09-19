package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow

interface FoodOutflowRepository {
    suspend fun createOutflow(outflow: NewFoodOutflow): Result<Unit>
}
