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

/**
 * [sanitizeDecimalInput] bounded to the food-intake quantity's server-side `Numeric(10,3)`: 7
 * integer digits + 3 decimals (`9999999.999`). Typing past the ceiling is silently dropped as the
 * user types rather than rejected on submit.
 */
fun sanitizeQuantityInput(raw: String): String {
    val normalized = sanitizeDecimalInput(raw)
    val firstDot = normalized.indexOf('.')
    if (firstDot == -1) return normalized.take(MAX_QUANTITY_INTEGER_DIGITS)
    val head = normalized.substring(0, firstDot).take(MAX_QUANTITY_INTEGER_DIGITS)
    val tail = normalized.substring(firstDot + 1).take(MAX_QUANTITY_DECIMALS)
    return "$head.$tail"
}

/** Digits only, capped at the same ceiling — for foods whose `base_unit` is `unidades`. */
fun sanitizeIntegerInput(raw: String): String =
    raw.filter(Char::isDigit).take(MAX_QUANTITY_INTEGER_DIGITS)

const val MAX_QUANTITY_INTEGER_DIGITS = 7
const val MAX_QUANTITY_DECIMALS = 3

/** The largest quantity the server's `Numeric(10,3)` column accepts. */
const val MAX_QUANTITY = 9_999_999.999
const val MAX_INTEGER_QUANTITY = 9_999_999
