package ar.com.ingenieriaalimentacion.presentation.feature.register

sealed interface RegisterAction {
    data class EmailChanged(val value: String) : RegisterAction
    data class PasswordChanged(val value: String) : RegisterAction
    data class NameChanged(val value: String) : RegisterAction
    data class LastNameChanged(val value: String) : RegisterAction
    data class EstablishmentToggled(val id: String) : RegisterAction
    data object Submit : RegisterAction
    data object DismissError : RegisterAction
}
