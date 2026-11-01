package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

sealed interface MeasurementAction {
    data object MeasurementDatePickerOpen : MeasurementAction
    data object MeasurementDatePickerDismiss : MeasurementAction
    data class MeasurementDateSelected(val date: LocalDate) : MeasurementAction
    data object MeasurementTimePickerOpen : MeasurementAction
    data object MeasurementTimePickerDismiss : MeasurementAction
    data class MeasurementTimeSelected(val time: LocalTime) : MeasurementAction
    data class ObservationChanged(val value: String) : MeasurementAction
    data class ObservationFocusChanged(val focused: Boolean) : MeasurementAction
    data class WeightChanged(val value: String) : MeasurementAction
    data class WeightFocusChanged(val focused: Boolean) : MeasurementAction
    data class HeightChanged(val value: String) : MeasurementAction
    data class HeightFocusChanged(val focused: Boolean) : MeasurementAction
    data class SubjectSelected(val subject: SubjectSummary) : MeasurementAction
    data object ChangeSubjectClick : MeasurementAction
    data object EstablishmentPickerOpen : MeasurementAction
    data object EstablishmentPickerDismiss : MeasurementAction
    data class EstablishmentSelected(val establishment: EstablishmentWithStatus) : MeasurementAction
    data class ConfirmMeasurement(val token: Long) : MeasurementAction
    data class DismissConfirmation(val token: Long) : MeasurementAction
    data object Submit : MeasurementAction
    data object DismissSuccess : MeasurementAction
    data object RetryProfile : MeasurementAction
    data object BackClick : MeasurementAction
}
