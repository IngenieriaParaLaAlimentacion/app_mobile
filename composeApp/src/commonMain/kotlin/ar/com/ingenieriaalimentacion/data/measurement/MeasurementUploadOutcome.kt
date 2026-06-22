package ar.com.ingenieriaalimentacion.data.measurement

import ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementResponse

/**
 * Typed result of POSTing one measurement, derived from the HTTP status. No `Duplicate`/`Merged`:
 * measurements have no canonical-dedup; a same-`id` replay returns 200 and is mapped to [Created].
 */
sealed interface MeasurementUploadOutcome {
    data class Created(val response: MeasurementResponse) : MeasurementUploadOutcome // 201 or 200
    data object NotFound : MeasurementUploadOutcome        // 404 → terminal
    data object Forbidden : MeasurementUploadOutcome       // 403 → terminal
    data object InvalidData : MeasurementUploadOutcome     // 422 → terminal
    data object Unauthorized : MeasurementUploadOutcome    // 401 → retry/dead-letter
    data class Retryable(val cause: Throwable?) : MeasurementUploadOutcome // network/5xx
}
