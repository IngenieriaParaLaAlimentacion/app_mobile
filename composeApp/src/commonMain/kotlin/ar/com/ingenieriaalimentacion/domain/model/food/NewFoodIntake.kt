package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * An intake ready to send. [id] is the client-generated UUID v4 idempotency key, held by the
 * ViewModel across retries so a retried submit cannot inflate stock: the server answers a replay of
 * the same id with 200 and leaves the balance untouched.
 *
 * [NewFoodIntakeLine.quantity] stays a normalized `String` (dot separator, at most 3 decimals) all
 * the way to the wire, so no float rounding reaches a balance the server accumulates.
 */
data class NewFoodIntake(
    val id: String,
    val establishmentId: String,
    val receivedAtEpochMillis: Long,
    val comments: String?,
    val originType: FoodIntakeOrigin,
    val isRecurring: Boolean,
    val items: List<NewFoodIntakeLine>,
)

data class NewFoodIntakeLine(
    val foodId: String,
    val quantity: String,
)
