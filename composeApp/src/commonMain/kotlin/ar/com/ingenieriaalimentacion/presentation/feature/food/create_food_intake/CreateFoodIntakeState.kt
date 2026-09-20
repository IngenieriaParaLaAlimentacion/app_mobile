package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

/** Longest `comments` the backend accepts. */
const val MAX_COMMENTS = 500

/**
 * One line of the intake. The food's [Food.unit] drives both the keyboard and the validator, so a
 * line effectively owns its own validation rule — see `CreateFoodIntakeViewModel.validatorFor`.
 */
data class FoodIntakeLine(
    val food: Food,
    val quantity: TextFieldState = TextFieldState(),
)

data class FoodSearchState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val total: Int = 0,
    val isSearching: Boolean = false,
    val error: Boolean = false,
    val isFocused: Boolean = false,
) {
    val isExpanded: Boolean get() = isFocused && query.isNotBlank()

    /**
     * "Mostrando 8 de 143 · seguí escribiendo" — the backend sorts alphabetically, not by
     * relevance, so a better match needs a narrower query, not another page.
     */
    val footer: String?
        get() = if (total > results.size) {
            "Mostrando ${results.size} de $total · seguí escribiendo"
        } else {
            null
        }

    /**
     * Empties the query and results but **keeps [isFocused]**. Picking a suggestion does not move
     * focus out of the field, so resetting the flag would leave state claiming "unfocused" while
     * the real field still holds focus — and [isExpanded] would then stay false, so typing the
     * next search would never reopen the dropdown.
     */
    fun cleared(): FoodSearchState = FoodSearchState(isFocused = isFocused)
}

data class CreateFoodIntakeState(
    val destination: CreateFoodIntakeDestination? = null,
    /** Client-generated idempotency key, stable across retries until the screen exits on success. */
    val intakeId: String = "",
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val selectedEstablishment: EstablishmentWithStatus? = null,
    /** Raw `DDMMYYYY` digit buffer behind `DateVisualTransformation`, pre-filled with today. */
    val receivedAt: TextFieldState = TextFieldState(),
    val comments: TextFieldState = TextFieldState(),
    val lines: List<FoodIntakeLine> = emptyList(),
    val search: FoodSearchState = FoodSearchState(),
    val isOnline: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    val commentsCount: String get() = "${comments.value.length}/$MAX_COMMENTS"

    /**
     * Gates on *presence*, not validity, mirroring `MeasurementState.canSubmit`: `submit()`
     * force-shows every error and refuses to build the model if anything is actually invalid.
     */
    val canSubmit: Boolean
        get() = isOnline && !isSubmitting && lines.isNotEmpty() && selectedEstablishment != null
}
