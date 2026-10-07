package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.Serializable

/**
 * Focused view of the `application/problem+json` error body of the verification endpoint.
 * Extra fields are ignored via `ignoreUnknownKeys`.
 *
 * [type] is the discriminator and it is not optional to check: the endpoint answers `401` for
 * an unknown token, a used one and an expired one, and only "used" means the email may
 * already be confirmed. Same approach as `data/food/dto/FoodIntakeProblem`.
 */
@Serializable
data class VerifyEmailProblem(val type: String? = null)

const val INVALID_VERIFICATION_TOKEN_TYPE = "/errors/auth/invalid-verification-token"
const val VERIFICATION_TOKEN_USED_TYPE = "/errors/auth/verification-token-used"
const val VERIFICATION_TOKEN_EXPIRED_TYPE = "/errors/auth/verification-token-expired"
