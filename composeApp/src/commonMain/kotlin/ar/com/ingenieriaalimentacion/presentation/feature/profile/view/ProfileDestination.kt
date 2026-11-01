package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

sealed class ProfileDestination {
    data class EditProfile(
        val name: String,
        val lastName: String,
        val email: String,
        val role: String,
    ) : ProfileDestination()

    data class MyEstablishments(val role: String) : ProfileDestination()
    data object LoggedOut : ProfileDestination()
}
