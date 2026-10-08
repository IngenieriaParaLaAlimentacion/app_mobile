package ar.com.ingenieriaalimentacion.presentation.feature.auth.verify_email

data class VerifyEmailState(
    val destination: VerifyEmailDestination? = null,
    val status: VerifyEmailStatus = VerifyEmailStatus.Verifying,
)

enum class VerifyEmailStatus {
    Verifying,
    Verified,
    AlreadyUsed,
    Expired,
    InvalidLink,
    ConnectionError,
}
