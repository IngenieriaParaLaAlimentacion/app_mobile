package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse

/** Typed result of POSTing one subject, derived from the HTTP status. */
sealed interface SubjectUploadOutcome {
    data class Created(val response: SubjectResponse) : SubjectUploadOutcome   // 201 or 200 (replay)
    data class Duplicate(val canonicalId: String) : SubjectUploadOutcome       // 409 + canonical_subject
    data object InvalidData : SubjectUploadOutcome                             // 400
    data object Unauthorized : SubjectUploadOutcome                            // 401 (after refresh failed)
    data object Forbidden : SubjectUploadOutcome                               // 403
    data class Retryable(val cause: Throwable?) : SubjectUploadOutcome         // network/5xx/unknown
}
