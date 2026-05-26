package ar.com.ingenieriaalimentacion.presentation.feature.settings

data class SettingsState(
    val isSigningOut: Boolean = false,
    val destination: SettingsDestination? = null,
)
