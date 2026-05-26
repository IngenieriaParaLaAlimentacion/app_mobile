package ar.com.ingenieriaalimentacion.presentation.feature.settings

sealed interface SettingsAction {
    data object SignOutClick : SettingsAction
}
