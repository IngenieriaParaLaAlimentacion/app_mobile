package ar.com.ingenieriaalimentacion.domain.model.subject

/** Outcome counts of a single sync pass, for logging/UX. */
data class SyncSummary(
    val synced: Int = 0,
    val merged: Int = 0,
    val failed: Int = 0,
    val stillPending: Int = 0,
)
