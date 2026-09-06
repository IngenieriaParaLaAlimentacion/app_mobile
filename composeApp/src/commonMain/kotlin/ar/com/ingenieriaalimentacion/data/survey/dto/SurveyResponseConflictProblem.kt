package ar.com.ingenieriaalimentacion.data.survey.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Focused view of the `409 application/problem+json` body. Extra fields are ignored via
 * `ignoreUnknownKeys`.
 *
 * [type] is the discriminator, and it is not optional to check: `POST /subjects` also answers `409`,
 * with a `canonical_subject` extension and a different `type`. Keying off the status alone would
 * conflate the two.
 *
 * Note `existing_response` is a **top-level sibling** of `type`/`title`/`status`, not nested under
 * an `extensions` object.
 */
@Serializable
data class SurveyResponseConflictProblem(
    val type: String? = null,
    @SerialName("conflict_on") val conflictOn: String? = null,
    @SerialName("existing_response") val existingResponse: ExistingResponseDto? = null,
)

@Serializable
data class ExistingResponseDto(
    val id: String,
    @SerialName("submitted_by") val submittedBy: SubmittedByDto? = null,
    @SerialName("event_at") val eventAt: String? = null,
    @SerialName("response_number") val responseNumber: Int? = null,
)

@Serializable
data class SubmittedByDto(val id: String, val name: String? = null)

const val SURVEY_RESPONSE_CONFLICT_TYPE = "/errors/survey-response-already-exists"
const val CONFLICT_ON_TARGET = "target"
const val CONFLICT_ON_RESPONSE_NUMBER = "response_number"
