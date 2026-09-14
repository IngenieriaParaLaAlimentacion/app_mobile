package ar.com.ingenieriaalimentacion.domain.model.food

import kotlinx.serialization.Serializable

/** Ingreso vs. salida. Two values rather than a boolean so the UI can label, icon and filter on it
 *  without a `if (isIntake)` at every call site. */
@Serializable
enum class FoodMovementDirection { Intake, Outflow }

/**
 * One persisted line of a movement. [quantity] is the server's `numeric(10,3)` as an exact decimal
 * **string** — never a `Double`: the value is money-like and float noise would show up as
 * "0,7499999" in the list. Format it with
 * [ar.com.ingenieriaalimentacion.presentation.common.formatQuantity].
 *
 * Two lines with the same [foodId] in one movement are legal and must both render.
 */
@Serializable
data class FoodMovementLine(
    val foodId: String,
    val foodName: String,
    val quantity: String,
    val unit: FoodUnit,
)

/**
 * One row of the "Alimentos" Historial tab: an ingreso or a salida this user registered.
 *
 * `@Serializable` because it round-trips through `CachedActivityItem.payloadJson`, exactly like
 * [ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary]. Any change to these
 * fields therefore needs a scoped cache-invalidation `.sqm`.
 *
 * There is no `SyncState` and no `isLocal`: food movements have no offline queue, so every row here
 * is by definition already on the server. There is no `source` either — the app renders no "origen
 * de la carga". [occurredAtMillis] is denormalized alongside the ISO [occurredAt] so sorting,
 * day-grouping and the pagination watermark all read the *same* parsed value.
 */
@Serializable
data class FoodMovement(
    val id: String,
    val direction: FoodMovementDirection,
    val occurredAt: String,
    val occurredAtMillis: Long,
    val establishmentName: String,
    val comments: String? = null,
    val lines: List<FoodMovementLine> = emptyList(),
)

/** "3 alimentos" / "1 alimento" — counts **lines**, not distinct foods, matching what is rendered. */
val FoodMovement.lineCountLabel: String
    get() = if (lines.size == 1) "1 alimento" else "${lines.size} alimentos"

/** Row headline, shared by the Alimentos tab and Home's "Actividad reciente". */
val FoodMovementDirection.movementTitle: String
    get() = when (this) {
        FoodMovementDirection.Intake -> "Ingreso de alimentos"
        FoodMovementDirection.Outflow -> "Salida de alimentos"
    }
