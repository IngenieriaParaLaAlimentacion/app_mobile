package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * Typed failure surfaced by the food-intake submit. Mirrors
 * [ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError].
 *
 * The two 404s are separate cases on purpose: they share a status code but mean different things to
 * the user, so the API branches on the problem+json `type`, not on the status alone.
 */
sealed interface FoodIntakeError {
    /** 401 — handled by the session machinery; the copy is a last resort. */
    data object Unauthorized : FoodIntakeError

    /** 403 `/errors/establishment-access-denied`. */
    data object EstablishmentAccessDenied : FoodIntakeError

    /** 404 `/errors/establishment-not-found`. */
    data object EstablishmentNotFound : FoodIntakeError

    /** 404 `/errors/food-not-found` — a live race, since a catalog food can be deleted outright. */
    data object FoodNotFound : FoodIntakeError

    /** 400 / 422, or a 404 whose `type` this build does not recognize. */
    data object InvalidData : FoodIntakeError

    /** Transport failure or 5xx — the only retryable case. */
    data object Network : FoodIntakeError

    data class Unknown(val cause: Throwable?) : FoodIntakeError
}

class FoodIntakeFailure(val error: FoodIntakeError, cause: Throwable?) :
    Exception(error.toString(), cause)
