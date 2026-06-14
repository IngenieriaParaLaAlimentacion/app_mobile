package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

sealed class ProfileDestination {
    data class EditProfile(
        val name: String,
        val lastName: String,
        val email: String,
        val role: String,
    ) : ProfileDestination()

    data object MyEstablishments : ProfileDestination()
    data object LoggedOut : ProfileDestination()
}
