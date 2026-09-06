package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodCategoryRefDto(val id: String, val name: String)

/**
 * One row of `GET /api/v1/food-catalog/foods`. [baseUnit] is the server's enum value
 * (`"unidades" | "kilogramos" | "litros"`) and is the sole source of a food's unit — the app never
 * lets the user pick one.
 */
@Serializable
data class FoodListItemDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("base_unit") val baseUnit: String,
    val category: FoodCategoryRefDto,
)

@Serializable
data class PaginatedFoodsResponse(
    val items: List<FoodListItemDto>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
