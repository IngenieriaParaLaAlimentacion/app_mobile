package ar.com.ingenieriaalimentacion.domain.model.survey

import kotlinx.serialization.Serializable

/** Who a survey is answered *for*. Decides which picker (if any) runs before the questionnaire,
 *  and which id the POST body carries. */
@Serializable
enum class SurveyResponseTarget { Subject, Establishment, Anonymous }

/** Summary shape from GET /surveys/available — no questions. */
data class Survey(
    val id: String,
    val name: String,
    val responseTarget: SurveyResponseTarget,
    val isActive: Boolean,
    val questionCount: Int,
    val requiresResponseNumber: Boolean? = null,
)

@Serializable
enum class SurveyResponseType { SingleChoice, MultipleChoice, Numeric, Text, Date }

@Serializable
data class SurveyOption(val id: String, val text: String)

/** `@Serializable` so [SurveyDetail] round-trips through `CachedSurvey.detailJson`, the same
 *  precedent as [ar.com.ingenieriaalimentacion.domain.model.subject.SyncState] round-tripping
 *  through `CachedActivityItem.payloadJson`. */
@Serializable
data class SurveyQuestion(
    val id: String,
    val text: String,
    val responseType: SurveyResponseType,
    val isRequired: Boolean,
    val options: List<SurveyOption>,
)

/** Full shape from GET /surveys/{id} — what the response form renders against. */
@Serializable
data class SurveyDetail(
    val id: String,
    val name: String,
    val responseTarget: SurveyResponseTarget,
    val isActive: Boolean,
    val questions: List<SurveyQuestion>,
    val requiresResponseNumber: Boolean? = null,
)
