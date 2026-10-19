package ar.com.ingenieriaalimentacion.domain.model.establishment

/** Typed failure of reading or replacing an establishment's characterization. */
sealed interface CharacterizationError {
    /** 401 — handled by the session machinery; the copy is a last resort. */
    data object Unauthorized : CharacterizationError

    /** 403 — the user has no approved access to this establishment (or is an analyst). */
    data object Forbidden : CharacterizationError

    /** 404 — the establishment no longer exists. */
    data object NotFound : CharacterizationError

    /**
     * 400 / 422. [field] is the backend's `field` extension: the snake_case body path of the first
     * field that failed (`premises_other`, `opening_hours`), or `null` when it was not reported.
     */
    data class InvalidField(val field: String?) : CharacterizationError

    /** Transport failure or 5xx. */
    data object Network : CharacterizationError

    data class Unknown(val cause: Throwable?) : CharacterizationError
}

class CharacterizationFailure(val error: CharacterizationError, cause: Throwable?) :
    Exception(error.toString(), cause)
