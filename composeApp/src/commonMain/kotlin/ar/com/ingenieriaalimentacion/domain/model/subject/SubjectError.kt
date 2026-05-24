package ar.com.ingenieriaalimentacion.domain.model.subject

sealed interface SubjectError {
    data object InvalidData : SubjectError
    data object Unauthorized : SubjectError
    data object Forbidden : SubjectError
    data object Network : SubjectError
    data class Unknown(val cause: Throwable?) : SubjectError
}
