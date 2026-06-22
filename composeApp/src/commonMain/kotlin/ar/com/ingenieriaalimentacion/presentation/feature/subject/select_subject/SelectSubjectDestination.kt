package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

sealed class SelectSubjectDestination {
    data object Back : SelectSubjectDestination()
    data class Confirm(val subjectJson: String) : SelectSubjectDestination()
}
