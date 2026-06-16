package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex

data class CreateSubjectState(
    val destination: CreateSubjectDestination? = null,
    val dni: TextFieldState = TextFieldState(),
    val sex: Sex? = null,
    val sexError: String? = null,
    val name: TextFieldState = TextFieldState(),
    val lastName: TextFieldState = TextFieldState(),
    val birthDate: TextFieldState = TextFieldState(),
    val isRural: Boolean = false,
    val isIndigenous: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = dni.value.isNotBlank() && sex != null && name.value.isNotBlank() &&
            lastName.value.isNotBlank() && birthDate.value.isNotBlank() && !isSubmitting
}
