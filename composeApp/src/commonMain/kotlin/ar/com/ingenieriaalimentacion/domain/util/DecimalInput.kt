package ar.com.ingenieriaalimentacion.domain.util

/** Normalizes free-typed decimal input (comma or dot separator, e.g. from a numeric measurement
 *  or survey-question field) to a single-dot, digits-only string as the user types. Shared by
 *  [ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.MeasurementViewModel]
 *  and the survey response form. */
fun sanitizeDecimalInput(raw: String): String {
    val normalized = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = normalized.indexOf('.')
    if (firstDot == -1) return normalized
    val head = normalized.substring(0, firstDot + 1)
    val tail = normalized.substring(firstDot + 1).filter { it.isDigit() }
    return head + tail
}
