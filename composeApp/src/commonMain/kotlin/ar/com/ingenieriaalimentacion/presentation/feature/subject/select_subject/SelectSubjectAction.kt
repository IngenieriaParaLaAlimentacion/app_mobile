package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

sealed interface SelectSubjectAction {
    data class QueryChanged(val value: String) : SelectSubjectAction
    data class SubjectClicked(val subject: SubjectSummary) : SelectSubjectAction
    data object LoadMore : SelectSubjectAction
    data object BackClick : SelectSubjectAction
    data object RetryClick : SelectSubjectAction
    data object CreateSubjectClick : SelectSubjectAction
}
