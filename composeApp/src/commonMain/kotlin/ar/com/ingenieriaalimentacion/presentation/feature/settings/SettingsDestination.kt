package ar.com.ingenieriaalimentacion.presentation.feature.settings

sealed class SettingsDestination {
    data object LoggedOut : SettingsDestination()
}
