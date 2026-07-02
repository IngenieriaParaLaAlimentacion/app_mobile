package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

sealed interface SubjectUploadsAction {
    data class Retry(val localId: String) : SubjectUploadsAction
    data class Delete(val localId: String) : SubjectUploadsAction
    data object LoadMore : SubjectUploadsAction
}
