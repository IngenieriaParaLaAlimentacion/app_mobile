package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodOutflowProblem(
    val type: String? = null,
    @SerialName("food_id") val foodId: String? = null,
    val requested: String? = null,
    val available: String? = null,
)

const val INSUFFICIENT_STOCK_TYPE = "/errors/insufficient-stock"
