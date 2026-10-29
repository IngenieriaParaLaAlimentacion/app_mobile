package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

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
    val measuredDateTime: LocalDateTime? = null,
    val measurementDateError: String? = null,
    val latestAllowedMeasurementDate: LocalDate? = null,
    val showMeasurementDatePicker: Boolean = false,
    val showMeasurementTimePicker: Boolean = false,
    val isSubmitting: Boolean = false,
    val confirmation: MeasurementConfirmation? = null,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = subject != null && selectedEstablishment != null &&
            measuredDateTime != null && measurementDateError == null &&
            weight.value.isNotBlank() && height.value.isNotBlank() && !isSubmitting && confirmation == null &&
            !showMeasurementDatePicker && !showMeasurementTimePicker
}
