package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex

sealed interface CreateSubjectAction {
    data class DniChanged(val value: String) : CreateSubjectAction
    data class SexChanged(val value: Sex) : CreateSubjectAction
    data class NameChanged(val value: String) : CreateSubjectAction
    data class LastNameChanged(val value: String) : CreateSubjectAction
    data class BirthDateChanged(val value: String) : CreateSubjectAction
    data class IsRuralChanged(val value: Boolean) : CreateSubjectAction
    data class IsIndigenousChanged(val value: Boolean) : CreateSubjectAction
    data object Submit : CreateSubjectAction
    data object DismissSuccess : CreateSubjectAction
    data object BackClick : CreateSubjectAction
}
