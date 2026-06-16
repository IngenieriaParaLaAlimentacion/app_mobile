package ar.com.ingenieriaalimentacion.core.validation

/** Validates a raw field [value], returning an error message or `null` when it passes. */
typealias Validator = (value: String) -> String?

/**
 * Reusable state for a single text input that wants blur-aware error display.
 *
 * The UX rule (see [displayError]): never surface an error while the user is
 * editing the field; only after they leave it (blur) — or after a submit forces
 * every field to show. [error] is recomputed eagerly on every change/blur so
 * [isValid] always reflects the current value; [displayError] decides *when* to
 * actually show it.
 *
 * Drive it from the ViewModel with [onChange] / [onFocus], force it open on
 * submit with [showError], and feed [displayError] into `AlInput(error = ...)`.
 */
data class TextFieldState(
    val value: String = "",
    val isFocused: Boolean = false,
    val hasBlurred: Boolean = false,
    val forceShow: Boolean = false,
    val error: String? = null,
) {
    /** `true` when the current [value] satisfies its rules. */
    val isValid: Boolean get() = error == null

    /**
     * The error to render, or `null` to render none. Hidden while the field is
     * focused (don't nag mid-typing) and while it's empty-and-only-blurred
     * (don't nag a field the user merely tabbed through) — but always shown once
     * [forceShow] is set by a submit attempt.
     */
    val displayError: String?
        get() = when {
            isFocused -> null
            forceShow -> error
            hasBlurred && value.isNotBlank() -> error
            else -> null
        }
}

/** Apply a new [value] (already sanitized by the caller) and recompute [TextFieldState.error]. */
fun TextFieldState.onChange(value: String, validator: Validator): TextFieldState =
    copy(value = value, error = validator(value))

/**
 * Record a focus transition. Marks the field blurred the first time it loses
 * focus and recomputes the error so a blur reveals any violation immediately.
 */
fun TextFieldState.onFocus(focused: Boolean, validator: Validator): TextFieldState =
    copy(
        isFocused = focused,
        hasBlurred = hasBlurred || !focused,
        error = validator(value),
    )

/** Re-validate and force the error to display regardless of focus/blur — used on submit. */
fun TextFieldState.showError(validator: Validator): TextFieldState =
    copy(forceShow = true, isFocused = false, error = validator(value))
