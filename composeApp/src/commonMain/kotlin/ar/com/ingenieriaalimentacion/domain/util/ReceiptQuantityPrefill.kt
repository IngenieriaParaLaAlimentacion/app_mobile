package ar.com.ingenieriaalimentacion.domain.util

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit

private data class PrintedUnit(val base: FoodUnit, val extraScale: Int)
private val decimalText = Regex("^[0-9]+(\\.[0-9]+)?$")

/** Returns a suggestion only when the printed measurement converts exactly into the food's base unit. */
fun exactReceiptPrefill(rawQuantity: String?, rawUnitText: String?, baseUnit: FoodUnit): String? {
    val raw = rawQuantity?.trim() ?: return null
    if (raw.length > 64 || !decimalText.matches(raw)) return null
    val token = rawUnitText?.trim()?.lowercase()?.removeSuffix(".") ?: return null
    val printed = when (token) {
        "kg", "kilo", "kilos", "kilogramo", "kilogramos" -> PrintedUnit(FoodUnit.Kilogramos, 0)
        "g", "gr", "gramo", "gramos" -> PrintedUnit(FoodUnit.Kilogramos, 3)
        "l", "lt", "lts", "litro", "litros" -> PrintedUnit(FoodUnit.Litros, 0)
        "ml", "mililitro", "mililitros" -> PrintedUnit(FoodUnit.Litros, 3)
        "u", "un", "unidad", "unidades" -> PrintedUnit(FoodUnit.Unidades, 0)
        else -> return null
    }
    if (printed.base != baseUnit) return null

    val fractional = raw.substringAfter('.', "")
    var digits = (raw.substringBefore('.') + fractional).trimStart('0')
    if (digits.isEmpty()) return null
    var scale = fractional.length + printed.extraScale
    while (scale > 0 && digits.endsWith('0')) {
        digits = digits.dropLast(1)
        scale--
    }
    if (scale > 3 || (baseUnit == FoodUnit.Unidades && scale != 0)) return null
    val wholeLength = digits.length - scale
    val whole = if (wholeLength > 0) digits.take(wholeLength) else "0"
    if (whole.length > 7) return null
    val remainder = when {
        scale == 0 -> ""
        wholeLength > 0 -> digits.drop(wholeLength)
        else -> digits.padStart(scale, '0')
    }
    return if (scale == 0) whole else "$whole.$remainder"
}
