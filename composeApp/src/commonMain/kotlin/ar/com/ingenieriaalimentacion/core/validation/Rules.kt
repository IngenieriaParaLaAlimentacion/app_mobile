package ar.com.ingenieriaalimentacion.core.validation

/**
 * Composable building blocks for [Validator]s. Combine them with [of], which
 * returns the first failing rule's message:
 *
 * ```kotlin
 * private val dniValidator: Validator = Rules.of(
 *     Rules.required("Ingresá el DNI."),
 *     Rules.digitsInRange(1..99_999_999, "Ingresá un DNI válido (1 a 8 dígitos)."),
 * )
 * ```
 *
 * Presence is the concern of [required]; the content rules ([maxLength],
 * [minLength], [matches], [digitsInRange]) deliberately pass on a blank value so
 * a single combined validator shows only "required" when empty and only the
 * format message when the user typed something invalid.
 */
object Rules {

    fun required(message: String): Validator =
        { value -> if (value.isBlank()) message else null }

    fun maxLength(max: Int, message: String): Validator =
        { value -> if (value.trim().length > max) message else null }

    fun minLength(min: Int, message: String): Validator =
        { value -> if (value.isNotBlank() && value.trim().length < min) message else null }

    fun matches(regex: Regex, message: String): Validator =
        { value -> if (value.isNotBlank() && !regex.matches(value.trim())) message else null }

    fun digitsInRange(range: IntRange, message: String): Validator =
        { value -> if (value.isNotBlank() && value.toIntOrNull().let { it == null || it !in range }) message else null }

    /**
     * Passes on blank (so [required] owns presence). Accepts ',' or '.' as the decimal separator;
     * enforces `min < x <= max` (exclusive-low, inclusive-high).
     */
    fun decimalInRange(min: Double, max: Double, message: String): Validator = { value ->
        val v = value.trim().replace(',', '.').toDoubleOrNull()
        if (value.isNotBlank() && (v == null || v <= min || v > max)) message else null
    }

    /** Runs [rules] in order, returning the first non-null (failing) message. */
    fun of(vararg rules: Validator): Validator =
        { value -> rules.firstNotNullOfOrNull { rule -> rule(value) } }
}
