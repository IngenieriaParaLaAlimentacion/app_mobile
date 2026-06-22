package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    @SerialName("get_started")
    data object GetStarted : Route()

    @Serializable
    @SerialName("home")
    data object Home : Route()

    @Serializable
    @SerialName("register")
    data object Register : Route()

    @Serializable
    @SerialName("register_success")
    data object RegisterSuccess : Route()

    @Serializable
    @SerialName("create_subject")
    data object CreateSubject : Route()

    @Serializable
    @SerialName("subject_uploads")
    data object SubjectUploads : Route()

    @Serializable
    @SerialName("select_establishment")
    data class SelectEstablishment(val preselectedJson: String = "[]") : Route()

    @Serializable
    @SerialName("select_subject")
    data object SelectSubject : Route()

    @Serializable
    @SerialName("reset_password")
    data class ResetPassword(val token: String) : Route()

    @Serializable
    @SerialName("reset_password_success")
    data object ResetPasswordSuccess : Route()

    @Serializable
    @SerialName("forgot_password")
    data object ForgotPassword : Route()

    @Serializable
    @SerialName("login")
    data object Login : Route()

    @Serializable
    @SerialName("profile")
    data object Profile : Route()

    @Serializable
    @SerialName("edit_profile")
    data class EditProfile(
        val name: String,
        val lastName: String,
        val email: String,
        val role: String,
    ) : Route()

    @Serializable
    @SerialName("request_establishment")
    data object RequestEstablishment : Route()

    @Serializable
    @SerialName("my_establishments")
    data object MyEstablishments : Route()

    @Serializable
    @SerialName("authed_shell")
    data object AuthedShell : Route()
}
