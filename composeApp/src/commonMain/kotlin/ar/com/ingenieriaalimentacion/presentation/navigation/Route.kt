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
    @SerialName("select_establishment")
    data class SelectEstablishment(val preselectedJson: String = "[]") : Route()

    @Serializable
    @SerialName("reset_password")
    data class ResetPassword(val token: String) : Route()

    @Serializable
    @SerialName("reset_password_success")
    data object ResetPasswordSuccess : Route()
}
