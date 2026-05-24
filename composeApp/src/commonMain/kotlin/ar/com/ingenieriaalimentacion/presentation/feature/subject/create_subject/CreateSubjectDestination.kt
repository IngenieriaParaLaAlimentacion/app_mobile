package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

sealed class CreateSubjectDestination {
    data object Back : CreateSubjectDestination()
}
