package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

sealed class SelectSubjectDestination {
    data object Back : SelectSubjectDestination()
    data class Confirm(val subjectJson: String) : SelectSubjectDestination()

    /** Subject not on file — create it inline and come back with it already selected. */
    data class CreateSubject(val prefillDni: String) : SelectSubjectDestination()
}
