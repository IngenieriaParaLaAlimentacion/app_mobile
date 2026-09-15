package ar.com.ingenieriaalimentacion.domain.model.profile

/**
 * Why a screen does or does not have a current establishment.
 *
 * `GetCurrentEstablishmentUseCase` returns `null` for three different situations and the caller
 * cannot tell them apart: nothing was ever picked, the stored id is no longer approved, or the
 * establishment list itself failed to load. Home can afford that (ADR-015: Home never blocks on it)
 * — a screen whose entire content is scoped to one establishment cannot, because "send the user to
 * a picker" is the right answer for the first two and exactly the wrong one for the third: offline,
 * on a cold cache, the picker has nothing to show either.
 */
sealed interface CurrentEstablishmentResult {
    /** A stored id resolved against the caller's approved list. */
    data class Selected(val establishment: EstablishmentWithStatus) : CurrentEstablishmentResult

    /** The list loaded fine; nothing was ever picked. [options] may legitimately be empty — that is
     *  a user with no approved establishment yet, which reads differently again. */
    data class NoneSelected(val options: List<EstablishmentWithStatus>) : CurrentEstablishmentResult

    /** A stored id exists but is not in the approved list any more (revoked, rejected, deleted). */
    data class StaleSelection(val options: List<EstablishmentWithStatus>) : CurrentEstablishmentResult

    /** `getMyEstablishments()` failed — offline on a cold cache, or a 5xx. Retryable; NOT a picker. */
    data class LoadFailed(val cause: Throwable?) : CurrentEstablishmentResult
}
