package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Deliberately independent of [ar.com.ingenieriaalimentacion.data.activity.dto.ActivityResponse] —
 * that shape is hardcoded to subject/measurement and would silently drop a `food_intake` /
 * `food_outflow` payload (the shared `Json` sets `ignoreUnknownKeys = true`). Same reasoning, and
 * same precedent, as [ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivityDto].
 *
 * One DTO tree serves BOTH directions: the two payloads are column-for-column identical on the
 * wire (the backend's `received_at` vs `occurred_at` asymmetry never surfaces — mobile reads only
 * the item-level `occurred_at`). The direction comes from which sibling key is populated, which is
 * in turn decided by the `type` this call filtered on.
 */
@Serializable
data class FoodActivityFoodRefResponse(val id: String, val name: String)

/** `city`/`province` are declared nullable even though the backend ships them non-null: this is a
 *  read path for a brand-new payload, and a null there must degrade, never crash the tab. */
@Serializable
data class FoodActivityEstablishmentResponse(
    val id: String,
    val name: String,
    val city: String? = null,
    val province: String? = null,
)

/**
 * One persisted line. [quantity] is decoded with [QuantitySerializer], whose read direction accepts
 * a JSON number *or* a string alike — so the server's `numeric(10,3)` arrives as an exact decimal
 * string and never round-trips through `Double`.
 *
 * Duplicate lines (the same [food] twice in one movement) are legal — there is no unique
 * `(movement_id, food_id)` constraint — and are preserved here verbatim. Never `distinctBy`.
 */
@Serializable
data class FoodActivityLineResponse(
    val food: FoodActivityFoodRefResponse,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
    val unit: String,
)

/**
 * `source` (`manual`/`photo`) is intentionally **not parsed**: the app renders no "origen de la
 * carga" affordance, so decoding it would create a client-side concept with no consumer.
 * `responsible` is likewise absent from the payload — `/me/activity` is already caller-scoped.
 */
@Serializable
data class FoodMovementActivityPayloadResponse(
    val id: String,
    val establishment: FoodActivityEstablishmentResponse,
    val comments: String? = null,
    val items: List<FoodActivityLineResponse> = emptyList(),
)

/** Exactly one of [foodIntake]/[foodOutflow] is populated, decided by the `type` filter used. */
@Serializable
data class FoodMovementActivityItemResponse(
    val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("food_intake") val foodIntake: FoodMovementActivityPayloadResponse? = null,
    @SerialName("food_outflow") val foodOutflow: FoodMovementActivityPayloadResponse? = null,
)

@Serializable
data class PaginatedFoodMovementActivityResponse(
    val items: List<FoodMovementActivityItemResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
