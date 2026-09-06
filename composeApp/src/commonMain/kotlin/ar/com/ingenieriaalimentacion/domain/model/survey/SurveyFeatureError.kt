package ar.com.ingenieriaalimentacion.domain.model.survey

/** Typed failure surfaced by the survey feature's Result-returning reads (survey detail, response
 *  detail). Mirrors [ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError]/`ProfileFailure`. */
sealed interface SurveyFeatureError {
    data object Forbidden : SurveyFeatureError
    data object NotFound : SurveyFeatureError
    data object Network : SurveyFeatureError
    data class Conflict(val existingResponseNumber: Int?, val submittedByName: String?) : SurveyFeatureError
    data class Unknown(val cause: Throwable?) : SurveyFeatureError
}

class SurveyFeatureFailure(val error: SurveyFeatureError, cause: Throwable?) :
    Exception(error.toString(), cause)
