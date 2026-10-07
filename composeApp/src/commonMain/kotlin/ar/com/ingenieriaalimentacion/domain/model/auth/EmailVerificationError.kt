package ar.com.ingenieriaalimentacion.domain.model.auth

/**
 * Typed failure of confirming an email through the emailed link.
 *
 * Kept apart from [AuthError] on purpose: the three token cases share HTTP 401 and are told
 * apart by the problem `type`, and only [TokenUsed] is compatible with "the email is already
 * confirmed". Folding them into [AuthError.InvalidOrExpiredToken] would lose that.
 */
sealed interface EmailVerificationError {
    /** 401 `/errors/auth/verification-token-used` — consumed, or superseded by a newer link. */
    data object TokenUsed : EmailVerificationError

    /** 401 `/errors/auth/verification-token-expired`. */
    data object TokenExpired : EmailVerificationError

    /** 401 `/errors/auth/invalid-verification-token`, an unrecognized 401, 400/422, or no token. */
    data object InvalidToken : EmailVerificationError

    /** Transport failure or 5xx — the only retryable case; the token is not consumed. */
    data object Network : EmailVerificationError
}

class EmailVerificationFailure(val error: EmailVerificationError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
