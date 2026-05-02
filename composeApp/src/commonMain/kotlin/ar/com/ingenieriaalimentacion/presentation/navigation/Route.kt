package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    @SerialName("home")
    data object Home : Route()
}
