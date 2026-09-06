package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.Serializable

/**
 * Focused view of the `application/problem+json` error body. Extra fields are ignored via
 * `ignoreUnknownKeys`.
 *
 * [type] is the discriminator, and it is not optional to check: `POST /food-intakes` answers `404`
 * both when the establishment is unknown and when a referenced food is, with different `type`s.
 * Keying off the status alone would conflate the two and show the wrong message.
 */
@Serializable
data class FoodIntakeProblem(val type: String? = null)

const val ESTABLISHMENT_NOT_FOUND_TYPE = "/errors/establishment-not-found"
const val FOOD_NOT_FOUND_TYPE = "/errors/food-not-found"
