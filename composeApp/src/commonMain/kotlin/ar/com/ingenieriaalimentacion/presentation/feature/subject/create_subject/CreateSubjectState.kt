package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex

data class CreateSubjectState(
    val destination: CreateSubjectDestination? = null,
    val dni: String = "",
    val sex: Sex? = null,
    val name: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val isRural: Boolean = false,
    val isIndigenous: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: CreateSubjectFieldErrors = CreateSubjectFieldErrors(),
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = dni.isNotBlank() && sex != null && name.isNotBlank() &&
            lastName.isNotBlank() && birthDate.isNotBlank() && !isSubmitting
}

data class CreateSubjectFieldErrors(
    val dni: String? = null,
    val sex: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val birthDate: String? = null,
)
