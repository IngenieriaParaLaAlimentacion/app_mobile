package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

data class MeasurementState(
    val destination: MeasurementDestination? = null,
    val subject: SubjectSummary? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val establishmentError: String? = null,
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val showEstablishmentPicker: Boolean = false,
    val weight: TextFieldState = TextFieldState(),
    val height: TextFieldState = TextFieldState(),
    val observation: TextFieldState = TextFieldState(),
    val measuredAtDisplay: String = "",            // friendly "ahora" for display (read-only)
    val isSubmitting: Boolean = false,
    val confirmation: MeasurementConfirmation? = null,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = subject != null && selectedEstablishment != null &&
            weight.value.isNotBlank() && height.value.isNotBlank() && !isSubmitting && confirmation == null
}
