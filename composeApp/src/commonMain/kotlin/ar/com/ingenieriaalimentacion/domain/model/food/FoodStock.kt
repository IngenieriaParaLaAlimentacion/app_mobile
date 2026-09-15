package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * One materialized stock balance row for an establishment.
 *
 * [quantity] is the server's `numeric(10,3)` carried as an exact decimal **string** (`"10.000"`),
 * never a `Double` — the same rule `QuantitySerializer` and `formatQuantity` enforce on the write
 * and render sides.
 *
 * [unit] comes from the catalog food's `base_unit` and is echoed on the stock row itself, so the
 * screen never joins against the catalog.
 *
 * [updatedAtMillis] is the **write** timestamp of the balance, not the movement date: a backdated
 * intake still stamps it with "now". The UI labels it "Actualizado" for exactly that reason, and is
 * null only when the server sent a timestamp this client cannot parse.
 */
data class FoodStockItem(
    val foodId: String,
    val foodName: String,
    val categoryId: String,
    val categoryName: String,
    val unit: FoodUnit,
    val quantity: String,
    val updatedAtMillis: Long?,
) {
    /** `quantity` is exact digits, so "depleted" is a string test, not a numeric comparison. */
    val isDepleted: Boolean get() = quantity.trimStart('0', '.', '-').none { it in '1'..'9' }
}

/**
 * One fetched page of the stock listing.
 *
 * Deliberately the newer `hasMore` collapse (as [FoodMovementHistoryPage]) rather than the older
 * full `PagedEstablishments` envelope: the screen consumes nothing but "is there another page", and
 * echoing `page` back to a caller that just asked for it is state that can only disagree.
 * `total_pages` is consumed in the mapper and never leaves the data layer.
 */
data class FoodStockPage(
    val items: List<FoodStockItem>,
    val hasMore: Boolean,
)

/**
 * Outbound sort tokens for `?sort_order=`.
 *
 * First **outbound** enum mapping in the app — every other wire enum is inbound and degrades in a
 * mapper. The direction reverses the rules: there is no unknown value to tolerate (we are the
 * producer), so the token lives on the enum constant and the compiler guarantees totality. Keep
 * [wireValue] in sync with the backend's `FoodStockSortOrder`; adding a sixth sort means adding a
 * constant here and nothing else.
 *
 * [label] sits next to it rather than in presentation, matching [FoodUnit.shortLabel] and
 * `FoodMovementDirection.movementTitle`, both of which already carry Spanish copy in domain.
 */
enum class StockSortOrder(val wireValue: String, val label: String) {
    NameAsc("name_asc", "Nombre (A–Z)"),
    NameDesc("name_desc", "Nombre (Z–A)"),
    QuantityDesc("quantity_desc", "Mayor cantidad"),
    QuantityAsc("quantity_asc", "Menor cantidad"),
    UpdatedAtDesc("updated_at_desc", "Actualizados recientemente"),
}

/**
 * The three listing knobs, moved as one value so a page request can never be issued with a filter
 * set that half-belongs to the previous page.
 *
 * [onlyAvailable] defaults to `true`: a field worker opening "Stock" is asking what there *is*, and
 * the depleted rows the backend deliberately keeps (`0.000`) would otherwise dominate the first
 * screen. The user reveals them by switching to "Todos".
 */
data class FoodStockFilters(
    val onlyAvailable: Boolean = true,
    val categoryId: String? = null,
    val sortOrder: StockSortOrder = StockSortOrder.NameAsc,
)

/**
 * Typed listing failure. Mirrors [FoodIntakeError]: the screen renders copy, not exceptions, and the
 * 403/404 split is the difference between "re-pick your establishment" and "retry".
 */
sealed interface FoodStockError {
    /** 401 — the session machinery handles this; the copy is a last resort. */
    data object Unauthorized : FoodStockError

    /** 403 `/errors/establishment-access-denied` — the relation is missing or not APPROVED. */
    data object EstablishmentAccessDenied : FoodStockError

    /** 404 `/errors/establishment-not-found` — the stored id points at nothing. */
    data object EstablishmentNotFound : FoodStockError

    /** 400 / 422, or a 404 whose `type` this build does not recognize. Terminal. */
    data object InvalidRequest : FoodStockError

    /** Transport failure or 5xx — the only retryable case. */
    data object Network : FoodStockError
}

class FoodStockFailure(val error: FoodStockError, cause: Throwable?) :
    Exception(error.toString(), cause)
