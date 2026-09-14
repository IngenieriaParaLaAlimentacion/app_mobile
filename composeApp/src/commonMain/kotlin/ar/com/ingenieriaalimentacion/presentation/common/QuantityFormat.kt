package ar.com.ingenieriaalimentacion.presentation.common

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit

/**
 * Renders a server `numeric(10,3)` quantity — carried as an exact decimal **string** — in Spanish:
 * comma decimal separator, trailing zeros trimmed, at most three decimals.
 *
 * `"0.750"` → `"0,75"` · `"12.000"` → `"12"` · `"1.005"` → `"1,005"` · `"7"` → `"7"`
 *
 * String-in/string-out on purpose: never parse to `Double` (float noise would turn `0.750` into
 * `0,7499999`), which is the same rule
 * [ar.com.ingenieriaalimentacion.data.food.dto.QuantitySerializer] enforces on the write side.
 * Deliberately NOT `HomeStateMapper`'s `formatMeasure` — that one is private, `Double`-based and
 * one-decimal, so it would round `0,750 kg` to `0,8 kg`.
 *
 * No thousands separator: quantities top out at 9.999.999,999 and a `.`-grouped integer next to a
 * `,`-decimal reads as noise at list density.
 *
 * Anything unparseable is returned unchanged — a raw value is more honest than a blank cell.
 */
fun formatQuantity(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val negative = trimmed.startsWith('-')
    val body = trimmed.removePrefix("-").removePrefix("+")
    val dot = body.indexOf('.')
    val intPart = (if (dot == -1) body else body.substring(0, dot)).ifEmpty { "0" }
    val fracPart = if (dot == -1) "" else body.substring(dot + 1).take(3).trimEnd('0')
    if (!intPart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return raw
    val sign = if (negative && (intPart.any { it != '0' } || fracPart.isNotEmpty())) "-" else ""
    return if (fracPart.isEmpty()) "$sign$intPart" else "$sign$intPart,$fracPart"
}

/** `"0.750"` + kilogramos → `"0,75 kg"`. The unit suffix reuses [FoodUnit.shortLabel]. */
fun formatQuantityWithUnit(raw: String, unit: FoodUnit): String =
    "${formatQuantity(raw)} ${unit.shortLabel}"
