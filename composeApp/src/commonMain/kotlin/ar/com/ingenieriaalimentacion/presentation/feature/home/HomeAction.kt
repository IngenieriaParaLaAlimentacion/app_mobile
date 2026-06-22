package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

sealed interface HomeAction {
    data object PrimaryClick : HomeAction
    data object CreateSubjectClick : HomeAction
    data object LoadMeasurementClick : HomeAction
    data class SubjectPicked(val subject: SubjectSummary) : HomeAction
    data object DismissPicked : HomeAction
}
