package ar.com.ingenieriaalimentacion.data.survey.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `survey_id` is absent on purpose — it is a path segment now, not a body field.
 *
 * Exactly one of [subjectId]/[establishmentId] per the survey's `response_target`, and **neither**
 * for an anonymous survey. `@EncodeDefault(NEVER)` is what makes "neither" work: the shared `Json`
 * sets `encodeDefaults = true` and leaves `explicitNulls` at its default, so without it a null
 * target would serialize as `"subject_id": null` and trip the server's "nothing may be sent to an
 * anonymous survey" 400. Flipping `explicitNulls` globally would change every request in the app.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SurveyResponseRequest(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("subject_id") val subjectId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("establishment_id") val establishmentId: String? = null,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

@Serializable
data class SurveyResponseEditRequest(
    val id: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
)

/** Shared by `POST`, `GET /{response_id}` and `PUT` — all three gained the two nullable target ids.
 *  `score_fies` is absorbed by `ignoreUnknownKeys`. */
@Serializable
data class SurveyResponseResponse(
    val id: String,
    @SerialName("survey_id") val surveyId: String,
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("establishment_id") val establishmentId: String? = null,
    @SerialName("submitted_by_id") val submittedById: String,
    val answers: Map<String, JsonElement>,
    @SerialName("event_at") val eventAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
