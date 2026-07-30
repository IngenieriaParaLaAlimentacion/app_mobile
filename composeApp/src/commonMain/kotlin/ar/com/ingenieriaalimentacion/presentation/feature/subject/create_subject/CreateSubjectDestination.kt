package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

sealed class CreateSubjectDestination {
    data object Back : CreateSubjectDestination()

    /** Saved from a flow that is waiting for the subject back (the measurement form). */
    data class Created(val subjectJson: String) : CreateSubjectDestination()
}
