package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * Unit a food's quantity is expressed in. It comes from the catalog's `base_unit` and is never
 * chosen by the user — the catalog decides it, and the wire body carries no unit at all.
 */
enum class FoodUnit {
    Unidades,
    Kilogramos,
    Litros,
    ;

    /** Short label rendered as the fixed suffix inside the quantity field. */
    val shortLabel: String
        get() = when (this) {
            Unidades -> "u"
            Kilogramos -> "kg"
            Litros -> "L"
        }
}

/** A catalog row as the typeahead needs it. */
data class Food(
    val id: String,
    val code: String,
    val name: String,
    val unit: FoodUnit,
    val categoryName: String,
)

/**
 * One page of catalog matches. [total] drives the "8 de 143" hint — the dropdown never paginates
 * (the backend orders alphabetically, not by relevance), it asks the user to type more.
 */
data class FoodSearchResult(
    val items: List<Food>,
    val total: Int,
)
