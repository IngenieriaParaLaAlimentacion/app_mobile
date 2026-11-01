package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.Serializable

/** Only what the app reads back: the intake is registered, and nothing on screen depends on the
 *  server's echo of the lines. Extra fields are ignored via `ignoreUnknownKeys`. */
@Serializable
data class FoodIntakeResponse(val id: String)
