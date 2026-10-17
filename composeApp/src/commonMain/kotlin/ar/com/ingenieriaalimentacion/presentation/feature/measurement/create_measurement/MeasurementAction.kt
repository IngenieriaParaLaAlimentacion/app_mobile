package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

sealed interface MeasurementAction {
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
