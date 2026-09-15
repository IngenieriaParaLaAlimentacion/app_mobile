package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backend's shared `IdentifierRefSchema` — `{id, name}`. Used for both `food` and `category` on
 * a stock row; neither exposes `code` here (only the catalog *listing* does).
 */
@Serializable
data class FoodStockRefDto(val id: String, val name: String)

/**
 * One row of `GET /api/v1/establishments/{id}/stock`.
 *
 * [quantity] uses [QuantitySerializer]: the server sends `numeric(10,3)` as a quoted JSON **string**
 * (`"10.000"`) on this endpoint, and `decodeString` under the shared lenient `Json` accepts a bare
 * number just as well, so the exact digits arrive without ever passing through `Double`.
 *
 * [updatedAt] is nullable purely defensively — the column is NOT NULL server-side, but a read path
 * must degrade, never crash the screen.
 */
@Serializable
data class FoodStockItemDto(
    val food: FoodStockRefDto,
    val category: FoodStockRefDto,
    val unit: String,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** The house envelope, re-declared per feature — there is no generic `PaginatedResponse<T>`. */
@Serializable
data class PaginatedFoodStockResponse(
    val items: List<FoodStockItemDto>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
