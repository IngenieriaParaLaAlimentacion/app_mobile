package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Focused view of the `409` problem+json body. Extra fields are ignored via `ignoreUnknownKeys`. */
@Serializable
data class SubjectConflictProblem(
    @SerialName("canonical_subject") val canonicalSubject: CanonicalSubjectDto? = null,
)

@Serializable
data class CanonicalSubjectDto(val id: String)
